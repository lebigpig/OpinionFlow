package com.lespider.opinionflow.web

import com.fasterxml.jackson.databind.JsonNode
import com.lespider.opinionflow.service.ChatMemoryService
import com.lespider.opinionflow.web.dto.ChatMemoryRequest
import com.lespider.opinionflow.web.dto.ChatMemoryHistoryResponse
import com.lespider.opinionflow.web.dto.ChatMemoryMessageDto
import com.lespider.opinionflow.web.dto.NewSessionResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/chat-memory")
class ChatMemoryController(
    private val chatMemoryService: ChatMemoryService,
) {
    /**
     * 获取所有 session 的摘要列表（用于前端 AI 分析菜单的历史回答列表）。
     * 前端打开 AI 分析菜单时调用此接口获取所有历史对话。
     */
    @GetMapping("/sessions")
    fun sessions(): List<ChatMemoryService.SessionSummary> {
        return chatMemoryService.listSessions()
    }

    /**
     * 创建新会话（runAiCustom 对应的接口）。
     * 返回一个新的 sessionId，前端后续用此 sessionId 进行对话。
     */
    @PostMapping("/new-session")
    fun newSession(): NewSessionResponse {
        val sessionId = chatMemoryService.generateSessionId()
        return NewSessionResponse(sessionId = sessionId)
    }

    /**
     * 带记忆的流式对话。
     * 前端发送 { sessionId, content, systemPrompt?, selectedContent? }，
     * selectedContent 为前端选中的历史回答文件完整内容，作为上下文记忆传入。
     * 后端以 SSE 格式流式返回 AI 回复（event: delta / event: done / event: error）。
     *
     * 对话内容会永久存储到 MySQL，Redis 缓存 20 分钟。
     */
    @PostMapping("/chat")
    fun chat(@RequestBody body: ChatMemoryRequest, response: HttpServletResponse) {
        val sessionId = body.sessionId?.trim().takeUnless { it.isNullOrEmpty() } ?: "default"
        val content = body.content?.trim().orEmpty()
        if (content.isEmpty()) {
            response.status = 400
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"error":"content 不能为空"}""")
            return
        }

        response.contentType = MediaType.TEXT_EVENT_STREAM_VALUE
        response.characterEncoding = "UTF-8"
        response.setHeader("Cache-Control", "no-cache")
        response.setHeader("Connection", "keep-alive")

        try {
            chatMemoryService.chatWithMemory(
                sessionId = sessionId,
                userContent = content,
                systemPrompt = body.systemPrompt,
                selectedContent = body.selectedContent,
            ) { delta ->
                val escaped = delta
                    .replace("\\", "\\\\")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                response.writer.write("event:delta\ndata:$escaped\n\n")
                response.writer.flush()
            }
            response.writer.write("event:done\ndata:{\"ok\":true}\n\n")
            response.writer.flush()
        } catch (e: Exception) {
            val msg = (e.message ?: "未知错误")
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            response.writer.write("event:error\ndata:$msg\n\n")
            response.writer.flush()
        }
    }

    /**
     * 获取指定 session 的对话历史（从 MySQL 读取，Redis 缓存 20min）。
     * 前端点击某条历史回答时，用该记录的 session_id 调用此接口。
     */
    @GetMapping("/history")
    fun history(@RequestParam sessionId: String): ChatMemoryHistoryResponse {
        val sid = sessionId.trim().takeIf { it.isNotEmpty() } ?: "default"
        val messages = chatMemoryService.getHistory(sid).map {
            ChatMemoryMessageDto(role = it.role, content = it.content)
        }
        return ChatMemoryHistoryResponse(sessionId = sid, messages = messages)
    }

    /**
     * 清除指定 session 的对话历史（MySQL + Redis 同步清除）
     */
    @PostMapping("/clear")
    fun clear(@RequestBody body: JsonNode) {
        val sid = body.path("sessionId").asText("default").trim().takeIf { it.isNotEmpty() } ?: "default"
        chatMemoryService.clearHistory(sid)
    }
}