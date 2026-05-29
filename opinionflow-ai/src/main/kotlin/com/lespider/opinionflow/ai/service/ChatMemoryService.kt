package com.lespider.opinionflow.ai.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lespider.opinionflow.ai.domain.ChatHistory
import com.lespider.opinionflow.ai.repo.ChatHistoryRepository
import com.lespider.opinionflow.api.rag.RagSearchRequest
import com.lespider.opinionflow.api.rag.RagSearchResult
import com.lespider.opinionflow.api.rag.RagFeignClient
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.model.StreamingResponseHandler
import dev.langchain4j.model.output.Response
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 基于 MySQL 永久化 + Redis 缓存的对话记忆服务。
 *
 * 设计思路：
 * - MySQL `chat_history` 表：永久存储每条对话消息（role / content / session_id）
 * - Redis（db3，20min TTL）：缓存最近对话内容，加速热读
 * - LangChain4j MessageWindowChatMemory：仅用于当前会话的滑动窗口（构建发送给 AI 的消息列表）
 * - 每次追问/回答完成后，追加写入 MySQL + 更新 Redis 缓存
 * - RAG：通过 Feign 调用 opinionflow-rag 服务检索相关新闻 → 注入 system prompt
 * - Agent 模式：webSearch=true 时，绑定 TavilySearchTool 供 AI 自主调用联网搜索
 */
@Service
class ChatMemoryService(
    private val objectMapper: ObjectMapper,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val redisTemplate: StringRedisTemplate,
    private val ragFeignClient: RagFeignClient,
    private val tavilySearchTool: TavilySearchTool,
    @Value("\${opinionflow.ai.api-url:}") private val apiUrl: String,
    @Value("\${opinionflow.ai.api-key:}") private val apiKey: String,
    @Value("\${opinionflow.ai.model:gpt-4o-mini}") private val model: String,
    @Value("\${opinionflow.rag.enabled:false}") private val ragEnabled: Boolean,
    @Value("\${opinionflow.rag.top-k:5}") private val ragTopK: Int,
    @Value("\${opinionflow.rag.max-distance:50.0}") private val ragMaxDistance: Double,
    @Value("\${opinionflow.rag.context-prefix:以下是与用户问题相关的新闻资料，供你参考分析：}") private val ragContextPrefix: String,
    @Value("\${opinionflow.tavily.enabled:false}") private val tavilyEnabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val REDIS_PREFIX = "chat:history:"
        private val REDIS_TTL = Duration.ofMinutes(20)
        private const val MAX_MESSAGES = 40
    }

    // ─── 数据类 ────────────────────────────────────────────────

    data class ChatMessageDto(val role: String, val content: String)

    data class SessionSummary(
        val sessionId: String,
        val preview: String,
        val messageCount: Int,
        val lastUpdated: String?,
    )

    fun generateSessionId(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16)
    }

    fun listSessions(): List<SessionSummary> {
        val sessionIds = chatHistoryRepository.findDistinctSessionIds()
        return sessionIds.map { sid ->
            val firstUser = chatHistoryRepository.findFirstUserMessage(sid)
            val lastMsg = chatHistoryRepository.findLastAssistantMessage(sid)
            val preview = firstUser?.content?.take(100) ?: "(无内容)"
            val messageCount = chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sid).size
            SessionSummary(
                sessionId = sid,
                preview = preview,
                messageCount = messageCount,
                lastUpdated = lastMsg?.createdAt?.toString() ?: firstUser?.createdAt?.toString(),
            )
        }
    }

    fun getHistory(sessionId: String): List<ChatMessageDto> {
        val cacheKey = REDIS_PREFIX + sessionId
        try {
            val cached = redisTemplate.opsForValue().get(cacheKey)
            if (cached != null) {
                return try {
                    objectMapper.readValue<List<ChatMessageDto>>(cached)
                } catch (_: Exception) {
                    loadFromDbAndCache(sessionId)
                }
            }
        } catch (_: Exception) {
            // Redis 不可用时降级到数据库查询
        }
        return loadFromDbAndCache(sessionId)
    }

    private fun loadFromDbAndCache(sessionId: String): List<ChatMessageDto> {
        val records = chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
        val messages = records.map { ChatMessageDto(role = it.role, content = it.content) }
        if (messages.isNotEmpty()) {
            val cacheKey = REDIS_PREFIX + sessionId
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(messages), REDIS_TTL)
            } catch (_: Exception) {
            }
        }
        return messages
    }

    @Transactional
    fun clearHistory(sessionId: String) {
        chatHistoryRepository.deleteBySessionId(sessionId)
        try {
            redisTemplate.delete(REDIS_PREFIX + sessionId)
        } catch (_: Exception) {
            // Redis 不可用时忽略删除操作
        }
    }

    // ─── 核心方法 ──────────────────────────────────────────────

    /**
     * 核心方法：流式 AI 对话（支持记忆 + RAG + Agent 自主联网搜索）。
     *
     * @param webSearch 是否启用 Agent 模式
     *                   - true: 绑定 TavilySearchTool，AI 自主决定是否联网搜索
     *                   - false: 传统纯文本对话（含 RAG 检索）
     */
    fun chatWithMemory(
        sessionId: String,
        userMessage: String,
        selectedContent: String? = null,
        webSearch: Boolean = false,
        onDelta: (String) -> Unit,
    ) {
        val trimmed = userMessage.trim()
        require(trimmed.isNotBlank()) { "消息内容不能为空" }

        // 1) 将用户消息写入 MySQL
        val userRecord = ChatHistory(
            sessionId = sessionId,
            role = "user",
            content = trimmed,
        )
        chatHistoryRepository.save(userRecord)

        // 2) 构建 system prompt（含 4a 选中新闻 + 4b RAG 检索）
        val sysText = buildSystemPrompt(trimmed, selectedContent)

        // 3) 构建 LangChain4j 流式模型
        val streamingModel = buildStreamingModel()

        if (webSearch && tavilyEnabled) {
            // ★★★ Agent 模式：AI 可自主调用 Tavily 联网搜索 ★★★
            log.info("[Agent] webSearch=true, tavilyEnabled=true → AI 可自主调用联网搜索")
            chatWithAgent(sessionId, streamingModel, sysText, trimmed, onDelta)
        } else {
            // ★ 传统模式：直接流式调用（含 RAG，原有逻辑保留）
            log.info("[Agent] webSearch={}, tavilyEnabled={} → 传统对话模式", webSearch, tavilyEnabled)
            chatSimple(sessionId, streamingModel, sysText, onDelta)
        }

        // 8) 更新 Redis 缓存
        invalidateCache(sessionId)
    }

    // ─── Agent 模式（手动工具调用循环）─────────────────────────

    /**
     * 手动实现的工具调用循环。
     *
     * 不使用 AiServices 自动递归流式绑定（避免 DeepSeek V4 递归 SSE 兼容性问题），
     * 改用 非流式模型做第一轮工具决策 + 流式模型做最终文本输出的两阶段方案。
     *
     * 流程：
     *   第1轮（非流式）→ AI 决定是否调用 webSearch
     *     - 若调用了工具：执行 Tavily 搜索 → 构造 ToolExecutionResultMessage → 追加到消息列表
     *   第2轮（流式）→ 用完整的消息列表（含工具执行结果）调用 streamingModel.generate() 获取最终文本
     */
    private fun chatWithAgent(
        sessionId: String,
        streamingModel: OpenAiStreamingChatModel,
        systemPrompt: String,
        userMessage: String,
        onDelta: (String) -> Unit,
    ) {
        // 构建非流式模型（用于第一轮工具决策）
        val nonStreamingModel = buildNonStreamingModel()

        // 构造 webSearch 工具的 ToolSpecification
        val webSearchToolSpec = ToolSpecification.builder()
            .name("webSearch")
            .description("""搜索互联网获取最新信息。当你需要查找实时新闻、最新数据、市场行情、突发事件等无法从已有知识中获取的信息时，请调用此工具。""")
            .parameters(JsonObjectSchema.builder()
                .addStringProperty("query", "精炼的搜索关键词，例如：'2026-05-29 A股 板块资金净流入 排名'")
                .required("query")
                .build())
            .build()

        // 1) 从 MySQL 加载历史消息
        val historyRecords = chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
        val messages = mutableListOf<ChatMessage>()
        messages.add(SystemMessage.from(systemPrompt))
        for (record in historyRecords) {
            when (record.role) {
                "user" -> messages.add(UserMessage.from(record.content))
                "assistant" -> messages.add(AiMessage.from(record.content))
            }
        }
        // 添加当前用户消息（已经保存到 DB，但 messages 列表中也需要）
        // 注意：historyRecords 已经包含刚保存的用户消息，所以不需要重复添加

        // ---- 第1轮：非流式调用，让 AI 决定是否调用工具 ----
        log.info("[Agent] 第1轮（非流式）：判断是否需要调用联网搜索工具")
        val firstRoundResponse: Response<AiMessage> = try {
            nonStreamingModel.generate(messages, listOf(webSearchToolSpec))
        } catch (e: Exception) {
            log.warn("[Agent] 非流式模型调用失败，降级到纯流式模式: {}", e.message)
            // 降级：直接进行流式调用（不包含工具规格）
            chatSimple(sessionId, streamingModel, systemPrompt, onDelta)
            return
        }

        val firstAiMessage = firstRoundResponse.content()
        if (firstAiMessage.hasToolExecutionRequests()) {
            // ---- AI 决定调用工具 ----
            val toolRequests = firstAiMessage.toolExecutionRequests()
            log.info("[Agent] AI 决定调用 {} 个工具", toolRequests.size)

            // 2a) 将 AI 的工具调用消息加入消息列表
            messages.add(firstAiMessage)

            // 2b) 逐个执行工具
            for (request in toolRequests) {
                if (request.name() == "webSearch") {
                    // 解析 arguments JSON，提取 query 字段（AI 用 function calling 传参时是 JSON 格式）
                    val rawArgs = request.arguments()
                    val query = try {
                        val jsonNode = objectMapper.readTree(rawArgs)
                        val extracted = jsonNode.get("query")?.asText() ?: rawArgs
                        log.info("[Agent Tool] 从 arguments JSON 提取 query: '{}'", extracted)
                        extracted
                    } catch (_: Exception) {
                        log.info("[Agent Tool] arguments 不是 JSON，直接使用: '{}'", rawArgs)
                        rawArgs
                    }
                    log.info("[Agent Tool] AI 执行工具: name='{}', query='{}'", request.name(), query)
                    val result = tavilySearchTool.webSearch(query)
                    log.info("[Agent Tool] 搜索完成，返回 {} 字符", result.length)
                    // 2c) 构造 ToolExecutionResultMessage 并加入消息列表
                    messages.add(ToolExecutionResultMessage.from(request, result))
                } else {
                    log.warn("[Agent Tool] 未知工具: name='{}'", request.name())
                    messages.add(ToolExecutionResultMessage.from(request, "未知工具"))
                }
            }

            // ---- 第2轮：流式调用，获取 AI 基于搜索结果的最终文本回复 ----
            log.info("[Agent] 第2轮（流式）：基于搜索结果生成最终文本回复")
            streamingChatGenerate(streamingModel, messages, sessionId, onDelta)
        } else {
            // ---- AI 决定不调用工具，直接流式回复 ----
            log.info("[Agent] AI 决定不调用工具，直接流式回复")
            streamingChatGenerate(streamingModel, messages, sessionId, onDelta)
        }
    }

    /**
     * 流式调用 AI 模型，并处理流式输出回调。
     */
    private fun streamingChatGenerate(
        streamingModel: OpenAiStreamingChatModel,
        messages: MutableList<ChatMessage>,
        sessionId: String,
        onDelta: (String) -> Unit,
    ) {
        val aiReply = StringBuilder()
        val future = CompletableFuture<Response<AiMessage>>()

        streamingModel.generate(messages, object : StreamingResponseHandler<AiMessage> {
            override fun onNext(token: String) {
                aiReply.append(token)
                onDelta(token)
            }

            override fun onComplete(response: Response<AiMessage>) {
                val finalText = response.content()?.text()
                if (finalText != null && aiReply.isEmpty()) {
                    aiReply.append(finalText)
                    onDelta(finalText)
                }
                future.complete(response)
            }

            override fun onError(error: Throwable) {
                future.completeExceptionally(error)
            }
        })

        try {
            future.get(120, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // 出错时删除刚写入的用户消息
            try {
                val lastUser = chatHistoryRepository
                    .findBySessionIdOrderByCreatedAtAsc(sessionId)
                    .lastOrNull { it.role == "user" }
                if (lastUser != null) chatHistoryRepository.delete(lastUser)
            } catch (_: Exception) {}
            val cause = e.cause ?: e
            error("AI 接口错误：${cause.message}")
        }

        // 保存 AI 回复到 MySQL
        if (aiReply.isNotEmpty()) {
            val aiRecord = ChatHistory(
                sessionId = sessionId,
                role = "assistant",
                content = aiReply.toString(),
            )
            chatHistoryRepository.save(aiRecord)
        }
    }

    // ─── 传统模式（保留原有全部逻辑）──────────────────────────

    private fun chatSimple(
        sessionId: String,
        streamingModel: OpenAiStreamingChatModel,
        systemPrompt: String,
        onDelta: (String) -> Unit,
    ) {
        // 2) 从 MySQL 恢复完整对话历史 → 构建 LangChain4j ChatMemory
        val historyRecords = chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
        val memory = MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES)
        for (record in historyRecords) {
            when (record.role) {
                "user" -> memory.add(UserMessage.from(record.content))
                "assistant" -> memory.add(AiMessage.from(record.content))
            }
        }

        // 5) 构建发送给 LangChain4j 模型的消息列表（原有逻辑）
        val chatMessages = mutableListOf<dev.langchain4j.data.message.ChatMessage>()
        chatMessages.add(dev.langchain4j.data.message.SystemMessage.from(systemPrompt))
        for (msg in memory.messages()) {
            when (msg) {
                is UserMessage, is AiMessage -> chatMessages.add(msg)
                else -> { /* 跳过 */ }
            }
        }

        // 6) 调用 AI 流式 API（原有逻辑）
        val aiReply = StringBuilder()
        val future = CompletableFuture<Response<AiMessage>>()

        streamingModel.generate(chatMessages, object : StreamingResponseHandler<AiMessage> {
            override fun onNext(token: String) {
                aiReply.append(token)
                onDelta(token)
            }

            override fun onComplete(response: Response<AiMessage>) {
                val finalText = response.content()?.text()
                if (finalText != null && aiReply.isEmpty()) {
                    aiReply.append(finalText)
                    onDelta(finalText)
                }
                future.complete(response)
            }

            override fun onError(error: Throwable) {
                future.completeExceptionally(error)
            }
        })

        try {
            future.get(120, TimeUnit.SECONDS)
        } catch (e: Exception) {
            chatHistoryRepository.delete(
                chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                    .lastOrNull { it.role == "user" } ?: return
            )
            val cause = e.cause ?: e
            error("AI 接口错误：${cause.message}")
        }

        // 7) 将 AI 回复写入 MySQL
        if (aiReply.isNotEmpty()) {
            val aiRecord = ChatHistory(
                sessionId = sessionId,
                role = "assistant",
                content = aiReply.toString(),
            )
            chatHistoryRepository.save(aiRecord)
        }
    }

    // ─── System Prompt 构建（含 3/4a/4b，原有逻辑）────────────

    private fun buildSystemPrompt(userMessage: String, selectedContent: String?): String {
        val sysBuilder = StringBuilder()

        // 3) 构建 system prompt（原有逻辑）
        sysBuilder.appendLine("你是一个专业的金融和新闻分析师。请根据对话历史和提供的新闻资料，为用户提供准确、有洞察力的分析。")
        sysBuilder.appendLine("请使用中文回答，语言要专业但易懂。")
        sysBuilder.appendLine()

        // 注入当前日期，让 AI 知道今天的日期
        val today = java.time.LocalDate.now()
        sysBuilder.appendLine("【重要】今天的日期是：${today}。在调用联网搜索工具时，请务必使用正确的日期来构造搜索关键词，例如今天是${today}，搜索最近行情应使用「${today}」而不是其他日期。")
        sysBuilder.appendLine()

        sysBuilder.appendLine("你可以使用「联网搜索工具」获取最新的实时信息。当用户询问的内容涉及最新事件、实时数据、市场行情、")
        sysBuilder.appendLine("如果用户明确要求搜索某些内容（如「搜索一下」「查一下」「看看最新的」），请务必调用搜索工具。")
        sysBuilder.appendLine()
        sysBuilder.appendLine("【重要】当你从联网搜索获取到结果后，请务必对搜索结果进行整理和解读，用自然流畅的中文文字描述呈现给用户，")
        sysBuilder.appendLine("不要直接输出搜索结果的原始格式（如表格、排行列表、JSON、URL链接等）。")
        sysBuilder.appendLine("总之，用户需要的是你的分析和解读，而不是搜索结果的直接搬运。")

        // 4a) 注入用户选中的新闻内容（如有）
        val ctxContent = selectedContent?.trim().takeUnless { it.isNullOrEmpty() }
        if (ctxContent != null) {
            sysBuilder.appendLine()
            sysBuilder.appendLine()
            sysBuilder.appendLine("以下是用户选中的新闻内容，你可以参考这些内容来回答用户的问题：")
            sysBuilder.appendLine(ctxContent)
        }

        // 4b) RAG 检索相关新闻注入 system prompt
        val forceRag = ctxContent.isNullOrEmpty()
        val shouldUseRag = forceRag || ragEnabled
        if (shouldUseRag) {
            val ragContext = retrieveRelevantNews(userMessage, force = forceRag)
            if (ragContext.isNotEmpty()) {
                sysBuilder.appendLine()
                sysBuilder.appendLine()
                if (ctxContent.isNullOrEmpty()) {
                    sysBuilder.appendLine("以下是通过智能检索找到的相关新闻资料，供你参考分析：")
                } else {
                    sysBuilder.appendLine(ragContextPrefix)
                }
                sysBuilder.appendLine(ragContext)
            }
        }

        return sysBuilder.toString()
    }

    // ─── RAG：通过 Feign 调用 RAG 服务 ──────────────────────────

    /**
     * 通过 Feign 调用 opinionflow-rag 服务进行向量检索。
     */
    private fun retrieveRelevantNews(userText: String, force: Boolean = false): String {
        if (!force && !ragEnabled) return ""

        try {
            val request = RagSearchRequest(
                query = userText,
                topK = ragTopK,
                maxDistance = ragMaxDistance,
            )

            log.info("[RAG] 调用 RAG 服务进行向量检索, query={}, topK={}", userText.take(50), ragTopK)
            val results: List<RagSearchResult> = ragFeignClient.search(request)
            log.info("[RAG] RAG 服务返回 {} 条结果", results.size)

            if (results.isEmpty()) return ""

            val sb = StringBuilder()
            var count = 0
            for (result in results) {
                if (result.content.isBlank()) continue
                count++
                sb.appendLine("【$count】来源: ${result.source} | 时间: ${result.publishTime} | 标题或时间: ${result.title} (相似度距离: ${String.format("%.4f", result.distance)})")
                val trimmedContent = if (result.content.length > 500) result.content.take(500) + "..." else result.content
                sb.appendLine("   内容: $trimmedContent")
                log.info("[RAG] 注入第 {} 条相关新闻到 system prompt, source={}, title={}, distance={},publishtime={}", count, result.source, result.title, String.format("%.4f", result.distance), result.publishTime)
                log.info("[RAG] 新闻内容: {}", trimmedContent.replace("\n", " "))
                sb.appendLine()
            }

            if (count > 0) {
                log.info("[RAG] 最终注入 {} 条相关新闻到 system prompt", count)
            }

            return sb.toString().trimEnd()
        } catch (e: Exception) {
            log.error("[RAG] 调用 RAG 服务异常", e)
            return ""
        }
    }

    // ─── 工具方法 ──────────────────────────────────────────────

    private fun invalidateCache(sessionId: String) {
        try {
            redisTemplate.delete(REDIS_PREFIX + sessionId)
        } catch (_: Exception) {
        }
    }

    private fun buildStreamingModel(): OpenAiStreamingChatModel {
        val builder = OpenAiStreamingChatModel.builder()
            .modelName(model)
            .logRequests(true)
            .logResponses(true)

        val baseUrl = chatCompletionsUrl()
        if (baseUrl.isNotBlank()) {
            builder.baseUrl(baseUrl)
        }
        if (apiKey.isNotBlank()) {
            builder.apiKey(apiKey)
        }

        return builder.build()
    }

    /**
     * 构建非流式模型，用于 Agent 模式第一轮工具决策调用。
     * 非流式调用更稳定可靠，避免 DeepSeek V4 SSE 递归兼容性问题。
     */
    private fun buildNonStreamingModel(): OpenAiChatModel {
        val builder = OpenAiChatModel.builder()
            .modelName(model)
            .logRequests(true)
            .logResponses(true)

        val baseUrl = chatCompletionsUrl()
        if (baseUrl.isNotBlank()) {
            builder.baseUrl(baseUrl)
        }
        if (apiKey.isNotBlank()) {
            builder.apiKey(apiKey)
        }

        return builder.build()
    }

    private fun chatCompletionsUrl(): String {
        val raw = apiUrl.trim()
        if (raw.isBlank()) return raw
        val normalized = raw.removeSuffix("/")
        if (normalized.contains("/chat/completions")) {
            return normalized.substringBefore("/chat/completions")
        }
        return normalized
    }
}
