package com.lespider.opinionflow.ai.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lespider.opinionflow.ai.domain.ChatHistory
import com.lespider.opinionflow.ai.repo.ChatHistoryRepository
import com.lespider.opinionflow.api.rag.RagSearchRequest
import com.lespider.opinionflow.api.rag.RagSearchResult
import com.lespider.opinionflow.api.rag.RagFeignClient
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.memory.chat.MessageWindowChatMemory
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
 */
@Service
class ChatMemoryService(
    private val objectMapper: ObjectMapper,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val redisTemplate: StringRedisTemplate,
    private val ragFeignClient: RagFeignClient,
    @Value("\${opinionflow.ai.api-url:}") private val apiUrl: String,
    @Value("\${opinionflow.ai.api-key:}") private val apiKey: String,
    @Value("\${opinionflow.ai.model:gpt-4o-mini}") private val model: String,
    @Value("\${opinionflow.rag.enabled:false}") private val ragEnabled: Boolean,
    @Value("\${opinionflow.rag.top-k:5}") private val ragTopK: Int,
    @Value("\${opinionflow.rag.max-distance:50.0}") private val ragMaxDistance: Double,
    @Value("\${opinionflow.rag.context-prefix:以下是与用户问题相关的新闻资料，供你参考分析：}") private val ragContextPrefix: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val REDIS_PREFIX = "chat:history:"
        private val REDIS_TTL = Duration.ofMinutes(20)
        private const val MAX_MESSAGES = 40
    }

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

    /**
     * 核心方法：流式 AI 对话（支持记忆 + RAG）。
     * RAG 检索通过 Feign 调用 opinionflow-rag 微服务完成。
     */
    fun chatWithMemory(
        sessionId: String,
        userMessage: String,
        selectedContent: String? = null,
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

        // 2) 从 MySQL 恢复完整对话历史 → 构建 LangChain4j ChatMemory
        val historyRecords = chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
        val memory = MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES)
        for (record in historyRecords) {
            when (record.role) {
                "user" -> memory.add(UserMessage.from(record.content))
                "assistant" -> memory.add(AiMessage.from(record.content))
            }
        }

        // 3) 构建 system prompt
        val sysBuilder = StringBuilder()
        sysBuilder.appendLine("你是一个专业的金融和新闻分析师。请根据对话历史和提供的新闻资料，为用户提供准确、有洞察力的分析。")
        sysBuilder.appendLine("请使用中文回答，语言要专业但易懂。")
        sysBuilder.appendLine("如果用户追问了「选中的历史报告内容」，请优先基于该内容展开分析。")

        // 4a) 注入用户选中的新闻内容（如有）
        val ctxContent = selectedContent?.trim().takeUnless { it.isNullOrEmpty() }
        if (ctxContent != null) {
            sysBuilder.appendLine()
            sysBuilder.appendLine()
            sysBuilder.appendLine("以下是用户选中的新闻内容，你可以参考这些内容来回答用户的问题：")
            sysBuilder.appendLine(ctxContent)
        }

        // 4b) RAG 检索相关新闻注入 system prompt
        //     - 如果用户未勾选新闻（ctxContent 为空），强制使用 RAG 检索相关内容作为上下文
        //     - 如果用户已勾选新闻，仅在 ragEnabled 开启时才额外检索
        val forceRag = ctxContent.isNullOrEmpty()  // 未选中新闻时强制 RAG 检索
        val shouldUseRag = forceRag || ragEnabled
        if (shouldUseRag) {
            val ragContext = retrieveRelevantNews(trimmed, force = forceRag)
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

        val sysText = sysBuilder.toString()

        // 5) 构建发送给 LangChain4j 模型的消息列表
        val streamingModel = buildStreamingModel()
        val chatMessages = mutableListOf<dev.langchain4j.data.message.ChatMessage>()
        chatMessages.add(dev.langchain4j.data.message.SystemMessage.from(sysText))
        for (msg in memory.messages()) {
            when (msg) {
                is UserMessage, is AiMessage -> chatMessages.add(msg)
                else -> { /* 跳过 */ }
            }
        }

        // 6) 调用 AI 流式 API
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
            chatHistoryRepository.delete(userRecord)
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

        // 8) 更新 Redis 缓存
        invalidateCache(sessionId)
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
                sb.appendLine("【$count】来源: ${result.source} | 时间: ${result.publishTime} | 标题: ${result.title} (相似度距离: ${String.format("%.4f", result.distance)})")
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