package com.lespider.opinionflow.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lespider.opinionflow.domain.ChatHistory
import com.lespider.opinionflow.repository.ChatHistoryRepository
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
    @Value("\${opinionflow.ai.api-url:}") private val apiUrl: String,
    @Value("\${opinionflow.ai.api-key:}") private val apiKey: String,
    @Value("\${opinionflow.ai.model:gpt-4o-mini}") private val model: String,
) {
    companion object {
        /** Redis 缓存前缀 */
        private const val REDIS_PREFIX = "chat:history:"
        /** Redis 缓存过期时间：20 分钟 */
        private val REDIS_TTL = Duration.ofMinutes(20)
        /** LangChain4j 滑动窗口最大消息数 */
        private const val MAX_MESSAGES = 40
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
     * 带记忆的流式对话。
     *
     * 流程：
     * 1) 将用户消息写入 MySQL
     * 2) 从 MySQL+Redis 恢复该 session 的完整对话历史
     * 3) 使用 LangChain4j 滑动窗口构建消息列表 + system prompt
     * 4) 调用 AI 流式 API
     * 5) AI 回复完成后，将 AI 回复写入 MySQL + 更新 Redis 缓存
     *
     * @param sessionId 会话 ID（前端点击历史回答时传入对应记录的 session_id）
     * @param userContent 用户输入内容
     * @param systemPrompt 可选的自定义 system prompt
     * @param selectedContent 可选的选中历史回答文件内容（作为上下文）
     * @param onDelta 流式回调
     */
    fun chatWithMemory(
        sessionId: String,
        userContent: String,
        systemPrompt: String? = null,
        selectedContent: String? = null,
        onDelta: (String) -> Unit,
    ) {
        val trimmed = userContent.trim()
        require(trimmed.isNotEmpty()) { "内容不能为空" }
        require(apiUrl.isNotBlank()) { "未配置 opinionflow.ai.api-url" }

        // 1) 将用户消息写入 MySQL
        val userRecord = ChatHistory(
            sessionId = sessionId,
            role = "user",
            content = trimmed,
        )
        chatHistoryRepository.save(userRecord)

        // 2) 从 MySQL 恢复完整对话历史（用于构建发送给 AI 的消息列表）
        val allRecords = chatHistoryRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)

        // 3) 使用 LangChain4j 滑动窗口管理消息数量
        val memory = MessageWindowChatMemory.builder()
            .maxMessages(MAX_MESSAGES)
            .build()

        // 将历史消息加载到滑动窗口记忆中
        for (record in allRecords) {
            when (record.role) {
                "user" -> memory.add(UserMessage.from(record.content))
                "assistant" -> memory.add(AiMessage.from(record.content))
            }
        }

        // 4) 构建 system prompt
        val sysBuilder = StringBuilder()
        val baseSys = systemPrompt?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "你是舆情与新闻分析助手。你可以参考历史分析报告和之前的对话来回答用户的问题。使用中文，条理清晰。"
        sysBuilder.append(baseSys)

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
        // 懒加载：下次读取时会自动从 MySQL 加载并缓存
    }

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