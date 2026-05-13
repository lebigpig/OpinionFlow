package com.lespider.opinionflow.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lespider.opinionflow.domain.ChatHistory
import com.lespider.opinionflow.repository.ChatHistoryRepository
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import dev.langchain4j.model.StreamingResponseHandler
import dev.langchain4j.model.output.Response
import io.milvus.client.MilvusServiceClient
import io.milvus.grpc.SearchResults
import io.milvus.param.R
import io.milvus.param.MetricType
import io.milvus.param.dml.SearchParam
import io.milvus.response.SearchResultsWrapper
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import jakarta.annotation.PostConstruct
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
 * - RAG：用户消息向量化 → Milvus 检索相关新闻 → 注入 system prompt
 *
 * 前端打开 AI 分析菜单时：
 * 1. 调用 /api/chat-memory/sessions 获取所有 session 列表（从 MySQL 读取）
 * 2. 点击某条历史回答时，用该记录的 session_id 调用 /api/chat-memory/history 获取对话历史
 * 3. 追问时，前端发送该 session_id，后端从 MySQL+Redis 恢复记忆后继续对话
 */
@Service
class ChatMemoryService(
    private val objectMapper: ObjectMapper,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val redisTemplate: StringRedisTemplate,
    private val milvusClient: MilvusServiceClient,
    @Value("\${opinionflow.ai.api-url:}") private val apiUrl: String,
    @Value("\${opinionflow.ai.api-key:}") private val apiKey: String,
    @Value("\${opinionflow.ai.model:gpt-4o-mini}") private val model: String,
    @Value("\${opinionflow.rag.enabled:false}") private val ragEnabled: Boolean,
    @Value("\${opinionflow.rag.collection-name:news_vectors}") private val ragCollectionName: String,
    @Value("\${opinionflow.rag.top-k:5}") private val ragTopK: Int,
    @Value("\${opinionflow.rag.max-distance:50.0}") private val ragMaxDistance: Double,
    @Value("\${opinionflow.rag.context-prefix:以下是与用户问题相关的新闻资料，供你参考分析：}") private val ragContextPrefix: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Redis 缓存前缀 */
        private const val REDIS_PREFIX = "chat:history:"
        /** Redis 缓存过期时间：20 分钟 */
        private val REDIS_TTL = Duration.ofMinutes(20)
        /** LangChain4j 滑动窗口最大消息数 */
        private const val MAX_MESSAGES = 40
        /** 向量维度（BGE-small-zh-v1.5 输出维度） */
        private const val VECTOR_DIM = 512
    }

    /** 本地嵌入模型（与 MilvusNewsImportService 使用同一模型，保证向量空间一致） */
    private lateinit var embeddingModel: EmbeddingModel

    @PostConstruct
    fun init() {
        embeddingModel = BgeSmallZhV15EmbeddingModel()
        if (ragEnabled) {
            log.info("[RAG] 已启用，集合: {}, topK: {}, maxDistance: {}", ragCollectionName, ragTopK, ragMaxDistance)
        } else {
            log.info("[RAG] 已禁用")
        }
    }

    /**
     * 用于 Controller 层返回给前端的简化消息 DTO
     */
    data class ChatMessageDto(val role: String, val content: String)

    /**
     * 会话摘要 DTO（用于历史列表展示）
     */
    data class SessionSummary(
        val sessionId: String,
        val preview: String,       // 第一条用户消息的截断预览
        val messageCount: Int,
        val lastUpdated: String?,
    )

    /**
     * 生成新的 session ID（用于 runAiCustom 创建新对话）
     */
    fun generateSessionId(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16)
    }

    /**
     * 获取所有 session 的摘要列表（从 MySQL 读取）。
     * 用于前端 AI 分析菜单打开时展示历史对话列表。
     */
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

    /**
     * 获取指定 session 的对话历史。
     * 优先从 Redis 缓存读取，缓存未命中则从 MySQL 读取并回填缓存。
     */
    fun getHistory(sessionId: String): List<ChatMessageDto> {
        // 1. 尝试从 Redis 读取
        val cacheKey = REDIS_PREFIX + sessionId
        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) {
            return try {
                objectMapper.readValue<List<ChatMessageDto>>(cached)
            } catch (_: Exception) {
                // 缓存数据损坏，从 MySQL 重新加载
                loadFromDbAndCache(sessionId)
            }
        }

        // 2. 缓存未命中，从 MySQL 读取并回填
        return loadFromDbAndCache(sessionId)
    }

    /**
     * 从 MySQL 加载对话历史并回填 Redis 缓存
     */
    private fun loadFromDbAndCache(sessionId: String): List<ChatMessageDto> {
        val records = chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
        val messages = records.map { ChatMessageDto(role = it.role, content = it.content) }

        // 回填 Redis 缓存
        if (messages.isNotEmpty()) {
            val cacheKey = REDIS_PREFIX + sessionId
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(messages), REDIS_TTL)
            } catch (_: Exception) {
                // Redis 写入失败不影响主流程
            }
        }

        return messages
    }

    /**
     * 清除指定 session 的对话历史（MySQL + Redis）
     */
    @Transactional
    fun clearHistory(sessionId: String) {
        chatHistoryRepository.deleteBySessionId(sessionId)
        redisTemplate.delete(REDIS_PREFIX + sessionId)
    }

    /**
     * 核心方法：流式 AI 对话（支持记忆 + RAG）。
     *
     * @param sessionId     会话 ID
     * @param userMessage   用户消息
     * @param selectedContent 用户选中的历史分析报告内容（可选，注入 system prompt）
     * @param onDelta       每个 token 的回调
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
                "user"      -> memory.add(UserMessage.from(record.content))
                "assistant" -> memory.add(AiMessage.from(record.content))
            }
        }

        // 3) 构建 system prompt
        val sysBuilder = StringBuilder()
        sysBuilder.appendLine("你是一个专业的金融和新闻分析师。请根据对话历史和提供的新闻资料，为用户提供准确、有洞察力的分析。")
        sysBuilder.appendLine("请使用中文回答，语言要专业但易懂。")
        sysBuilder.appendLine("如果用户追问了「选中的历史报告内容」，请优先基于该内容展开分析。")

        // 4a) RAG：检索相关新闻并注入 system prompt
        if (ragEnabled) {
            val ragContext = retrieveRelevantNews(trimmed)
            if (ragContext.isNotEmpty()) {
                sysBuilder.appendLine()
                sysBuilder.appendLine()
                sysBuilder.appendLine(ragContextPrefix)
                sysBuilder.appendLine(ragContext)
            }
        }

        // 4b) 注入用户选中的历史分析报告内容
        val ctxContent = selectedContent?.trim().takeUnless { it.isNullOrEmpty() }
        if (ctxContent != null) {
            sysBuilder.appendLine()
            sysBuilder.appendLine()
            sysBuilder.appendLine("以下是用户选中的历史分析报告内容，你可以参考这些内容来回答用户的问题：")
            sysBuilder.appendLine(ctxContent)
        }

        val sysText = sysBuilder.toString()

        // 5) 构建发送给 LangChain4j 模型的消息列表
        val streamingModel = buildStreamingModel()
        val chatMessages = mutableListOf<dev.langchain4j.data.message.ChatMessage>()
        chatMessages.add(dev.langchain4j.data.message.SystemMessage.from(sysText))
        for (msg in memory.messages()) {
            when (msg) {
                is UserMessage, is AiMessage -> chatMessages.add(msg)
                else -> { /* 跳过 SystemMessage 等 */ }
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

        // 等待流式响应完成
        try {
            future.get(120, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // 请求失败，从 MySQL 中删除刚才写入的用户消息
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

        // 8) 更新 Redis 缓存（整个 session 的对话列表）
        invalidateCache(sessionId)
        System.out.println(sysBuilder.toString())
        // 懒加载：下次读取时会自动从 MySQL 加载并缓存
    }

    // ─── RAG：向量检索相关新闻 ──────────────────────────────────

    /**
     * 将用户消息向量化 → 在 Milvus 中检索最相似的新闻 → 返回格式化文本。
     *
     * 注意：BGE-small-zh-v1.5 是中文优化模型，对中文的语义区分度较好。
     *       L2 距离通常在 0~1.5 范围内，max-distance 建议设为 ≥ 50.0。
     *       即使超过阈值，也会兜底取最相似的 top-k 条注入。
     */
    private fun retrieveRelevantNews(userText: String): String {
        if (!ragEnabled) return ""

        try {
            // 1. 对用户消息进行向量化
            val segment = TextSegment.from(userText)
            val embedding: Embedding = embeddingModel.embed(segment).content()
            val queryVector = embedding.vectorAsList()
            log.info("[RAG] 用户消息已向量化，维度: {}, 查询: {}", queryVector.size, userText.take(50))
            // 将完整向量写入项目根目录的 txt 文件，方便在 Attu 中手动搜索对比
            try {
                val vectorFile = java.io.File("rag_query_vector.txt")
                vectorFile.writeText(queryVector.joinToString(", "))
                log.info("[RAG] 完整向量已写入文件: {}, 维度: {}", vectorFile.absolutePath, queryVector.size)
            } catch (e: Exception) {
                log.warn("[RAG] 写入向量文件失败: {}", e.message)
            }

            // 2. 在 Milvus 中搜索最相似的新闻（多取一些候选以便兜底）
            val searchParam = SearchParam.newBuilder()
                .withCollectionName(ragCollectionName)
                .withVectorFieldName("vector")
                .withTopK(ragTopK * 2)
                .withMetricType(MetricType.L2)
                .withOutFields(listOf("source", "title", "content", "publish_time"))
                .withExpr("")
                .withFloatVectors(listOf(queryVector))
                .build()

            log.info("[RAG] 搜索参数: collection={}, topK={}, metricType=L2", ragCollectionName, ragTopK * 2)

            val searchResp: R<SearchResults> = milvusClient.search(searchParam)
            log.info("[RAG] 搜索响应状态: {}, 是否成功: {}", searchResp.status, searchResp.status == R.success<SearchResults>()!!.status)

            if (searchResp.status != R.success<SearchResults>()!!.status) {
                log.warn("[RAG] Milvus 搜索失败: {}", searchResp.message)
                return ""
            }

            // 3. 解析搜索结果
            val data = searchResp.getData()
            if (data == null) {
                log.warn("[RAG] 搜索响应 getData() 为 null")
                return ""
            }

            val milvusResults = data.getResults()
            if (milvusResults == null) {
                log.warn("[RAG] 搜索响应 getResults() 为 null")
                return ""
            }

            log.info("[RAG] Results fieldsDataCount={}", milvusResults.getFieldsDataCount())
            // 用 toString() 打印原始结果结构用于调试
            log.info("[RAG] Results toString (前500字符)={}", milvusResults.toString().take(500))

            val wrapper = SearchResultsWrapper(milvusResults)

            // 尝试多种方式获取结果
            log.info("[RAG] === 尝试 getIDScore(0) ===")
            val idScores0 = try { wrapper.getIDScore(0) } catch (e: Exception) { 
                log.warn("[RAG] getIDScore(0) 异常: {}", e.message)
                emptyList()
            }
            log.info("[RAG] getIDScore(0) 返回 {} 条结果", idScores0.size)

            // 如果 getIDScore(0) 为空，尝试其他下标
            if (idScores0.isEmpty()) {
                // 尝试 getIDScore(1)
                try {
                    val idScores1 = wrapper.getIDScore(1)
                    log.info("[RAG] getIDScore(1) 返回 {} 条结果", idScores1.size)
                } catch (e: Exception) {
                    log.info("[RAG] getIDScore(1) 异常: {}", e.message)
                }

                // 打印原始 protobuf 结构用于调试
                log.info("[RAG] milvusResults protobuf (前2000字符)={}", milvusResults.toString().take(2000))

                log.info("[RAG] Milvus 返回空结果集（所有方式都为空）")
                return ""
            }

            // 打印所有结果的距离用于调试
            for (i in idScores0.indices) {
                val score = idScores0[i].score
                val title = idScores0[i].get("title")?.toString() ?: ""
                log.info("[RAG] 搜索结果[{}]: 距离={}, 标题={}", i, score, title.take(40))
            }

            val idScores = idScores0

            // 4. 按距离阈值过滤，但如果全部超过阈值，取最相似的 top-k 条作为兜底
            val filtered = idScores.filter { it.score <= ragMaxDistance }
            val candidates = if (filtered.isNotEmpty()) filtered.take(ragTopK) else {
                log.warn("[RAG] 所有结果距离 > {}（最近: {}），使用兜底模式取最相似的 {} 条",
                    ragMaxDistance, idScores.first().score, ragTopK)
                idScores.take(ragTopK)
            }

            val results = StringBuilder()
            var count = 0

            for (idScore in candidates) {
                val score = idScore.score
                val title = idScore.get("title")?.toString() ?: ""
                val content = idScore.get("content")?.toString() ?: ""
                val source = idScore.get("source")?.toString() ?: ""
                val publishTime = idScore.get("publish_time")?.toString() ?: ""

                if (content.isBlank()) continue

                count++
                results.appendLine("【$count】来源: $source | 时间: $publishTime | 标题: $title (相似度距离: ${String.format("%.4f", score)})")
                // 截取内容前 500 字符避免 prompt 过长
                val trimmedContent = if (content.length > 500) content.take(500) + "..." else content
                results.appendLine("   内容: $trimmedContent")
                results.appendLine()
            }

            if (count > 0) {
                log.info("[RAG] 最终注入 {} 条相关新闻到 system prompt", count)
            }

            return results.toString().trimEnd()
        } catch (e: Exception) {
            log.error("[RAG] 向量检索异常", e)
            return ""
        }
    }

    // ─── 工具方法 ──────────────────────────────────────────────

    /**
     * 使指定 session 的 Redis 缓存失效
     */
    private fun invalidateCache(sessionId: String) {
        try {
            redisTemplate.delete(REDIS_PREFIX + sessionId)
        } catch (_: Exception) {
            // Redis 操作失败不影响主流程
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