package com.lespider.opinionflow.ai.controller

import com.lespider.opinionflow.ai.service.AiParseService
import com.lespider.opinionflow.ai.dto.AiParseRequest
import com.lespider.opinionflow.ai.dto.AiParseResponse
import java.util.concurrent.Executors
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/ai")
class AiController(
    private val aiParseService: AiParseService,
) {
    @PostMapping("/parse")
    fun parse(@RequestBody body: AiParseRequest): AiParseResponse {
        val content = body.content?.trim().orEmpty()
        val systemPrompt = body.systemPrompt?.trim()
        if (content.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "content 不能为空")
        }
        return try {
            AiParseResponse(result = aiParseService.parse(content, systemPrompt))
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }
    }

    /**
     * 世界格局地图智能 Agent：解析用户的自然语言指令，返回结构化 JSON 文本。
     * 与 /parse 使用不同地址，便于前端按场景路由。
     * body: { content: "用户指令", systemPrompt: "约束输出 JSON 的系统提示" }
     */
    @PostMapping("/world-map-agent")
    fun worldMapAgent(@RequestBody body: AiParseRequest): AiParseResponse {
        val content = body.content?.trim().orEmpty()
        val systemPrompt = body.systemPrompt?.trim()
        if (content.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "content 不能为空")
        }
        return try {
            AiParseResponse(result = aiParseService.parse(content, systemPrompt))
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }
    }

    @PostMapping("/parse/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun parseStream(@RequestBody body: AiParseRequest): SseEmitter {
        val content = body.content?.trim().orEmpty()
        val systemPrompt = body.systemPrompt?.trim()
        if (content.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "content 不能为空")
        }

        val emitter = SseEmitter(0L)
        val executor = Executors.newSingleThreadExecutor()

        executor.submit {
            try {
                aiParseService.parseStream(content, systemPrompt) { delta ->
                    emitter.send(SseEmitter.event().name("delta").data(delta))
                }
                emitter.complete()
            } catch (e: Exception) {
                emitter.completeWithError(e)
            } finally {
                executor.shutdown()
            }
        }

        return emitter
    }
}