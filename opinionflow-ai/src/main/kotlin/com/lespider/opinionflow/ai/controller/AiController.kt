package com.lespider.opinionflow.ai.controller

import com.lespider.opinionflow.ai.service.AiParseService
import com.lespider.opinionflow.ai.dto.AiParseRequest
import com.lespider.opinionflow.ai.dto.AiParseResponse
import com.lespider.opinionflow.ai.dto.AiRequestHeaders
import java.util.concurrent.Executors
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * AI 解析接口。
 *
 * 三个接口均可携带「AI 设置」请求头（见 [AiRequestHeaders]）来覆盖服务端默认 token / baseUrl / model，
 * 未携带时行为与之前完全一致（走 key.properties / application.yml 的默认配置）。
 */
@RestController
@RequestMapping("/api/ai")
class AiController(
    private val aiParseService: AiParseService,
) {
    @PostMapping("/parse")
    fun parse(
        @RequestBody body: AiParseRequest,
        @RequestHeader(value = AiRequestHeaders.PROVIDER, required = false) provider: String?,
        @RequestHeader(value = AiRequestHeaders.BASE_URL, required = false) baseUrl: String?,
        @RequestHeader(value = AiRequestHeaders.API_KEY, required = false) apiKey: String?,
        @RequestHeader(value = AiRequestHeaders.MODEL, required = false) model: String?,
    ): AiParseResponse {
        val content = body.content?.trim().orEmpty()
        val systemPrompt = body.systemPrompt?.trim()
        if (content.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "content 不能为空")
        }
        return try {
            AiParseResponse(
                result = aiParseService.parse(
                    content = content,
                    systemPrompt = systemPrompt,
                    override = AiRequestHeaders.toConfig(provider, baseUrl, apiKey, model),
                ),
            )
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
    fun worldMapAgent(
        @RequestBody body: AiParseRequest,
        @RequestHeader(value = AiRequestHeaders.PROVIDER, required = false) provider: String?,
        @RequestHeader(value = AiRequestHeaders.BASE_URL, required = false) baseUrl: String?,
        @RequestHeader(value = AiRequestHeaders.API_KEY, required = false) apiKey: String?,
        @RequestHeader(value = AiRequestHeaders.MODEL, required = false) model: String?,
    ): AiParseResponse {
        val content = body.content?.trim().orEmpty()
        val systemPrompt = body.systemPrompt?.trim()
        if (content.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "content 不能为空")
        }
        return try {
            AiParseResponse(
                result = aiParseService.parse(
                    content = content,
                    systemPrompt = systemPrompt,
                    override = AiRequestHeaders.toConfig(provider, baseUrl, apiKey, model),
                ),
            )
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }
    }

    @PostMapping("/parse/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun parseStream(
        @RequestBody body: AiParseRequest,
        @RequestHeader(value = AiRequestHeaders.PROVIDER, required = false) provider: String?,
        @RequestHeader(value = AiRequestHeaders.BASE_URL, required = false) baseUrl: String?,
        @RequestHeader(value = AiRequestHeaders.API_KEY, required = false) apiKey: String?,
        @RequestHeader(value = AiRequestHeaders.MODEL, required = false) model: String?,
    ): SseEmitter {
        val content = body.content?.trim().orEmpty()
        val systemPrompt = body.systemPrompt?.trim()
        if (content.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "content 不能为空")
        }

        // 注意：SseEmitter 的推流在独立线程执行，ThreadLocal 无法跨线程传递，
        // 因此这里把请求级配置作为方法参数显式传入（而非用 AiRuntimeConfigManager）。
        val aiConfig = AiRequestHeaders.toConfig(provider, baseUrl, apiKey, model)

        val emitter = SseEmitter(0L)
        val executor = Executors.newSingleThreadExecutor()

        executor.submit {
            try {
                aiParseService.parseStream(content, systemPrompt, aiConfig) { delta ->
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