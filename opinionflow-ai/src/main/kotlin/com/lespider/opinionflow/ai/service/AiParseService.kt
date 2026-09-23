package com.lespider.opinionflow.ai.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lespider.opinionflow.ai.dto.AiRuntimeConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class AiParseService(
    private val objectMapper: ObjectMapper,
    @Value("\${opinionflow.ai.api-url:}") private val apiUrl: String,
    @Value("\${opinionflow.ai.api-key:}") private val apiKey: String,
    @Value("\${opinionflow.ai.model:gpt-4o-mini}") private val model: String,
) {
    private val restClient: RestClient = RestClient.builder().build()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(java.time.Duration.ofSeconds(30))
        .build()

    /**
     * 非流式 AI 解析。
     *
     * @param override 请求级 AI 配置（前端「AI 设置」传入的 api-token / baseUrl / model），
     *                 优先级高于配置文件默认值；为 null 时完全走服务端默认配置。
     */
    fun parse(content: String, systemPrompt: String? = null, override: AiRuntimeConfig? = null): String {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "内容不能为空" }
        val baseUrl = resolveBaseUrl(override)
        require(baseUrl.isNotBlank()) { "未配置 opinionflow.ai.api-url" }

        val sys = systemPrompt?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "你是舆情与新闻分析助手。请阅读用户给出的正文，输出：要点摘要、情绪/立场倾向（如有）、关键词与可跟进建议。使用中文，条理清晰。"

        val body = objectMapper.createObjectNode().apply {
            put("model", resolveModel(override))
            set<JsonNode>(
                "messages",
                objectMapper.createArrayNode().apply {
                    add(
                        objectMapper.createObjectNode().apply {
                            put("role", "system")
                            put("content", sys)
                        },
                    )
                    add(
                        objectMapper.createObjectNode().apply {
                            put("role", "user")
                            put("content", trimmed)
                        },
                    )
                },
            )
        }

        val effectiveApiKey = resolveApiKey(override)
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(AiUrlNormalizer.chatCompletionsUrl(baseUrl)))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        if (effectiveApiKey.isNotBlank()) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer $effectiveApiKey")
        }
        val request = requestBuilder
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build()

        var response: HttpResponse<String>? = null
        try {
            // 用 HttpClient 直接读字符串，彻底绕开 RestClient converter 的 JSON→String/byte[] 问题
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (e: Exception) {
            // Connection reset 等临时网络错误：重试一次
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            } catch (e2: Exception) {
                error("AI 接口调用失败：${e2.message}")
            }
        }
        val res = response ?: error("AI 接口调用失败")
        if (res.statusCode() !in 200..299) {
            error("AI 接口错误 HTTP ${res.statusCode()}: ${res.body()}")
        }
        val responseText = res.body().trim()
        if (responseText.isEmpty()) error("AI 接口返回空响应")

        val root = objectMapper.readTree(responseText)
        val text = root.path("choices").path(0).path("message").path("content").asText(null)
        if (!text.isNullOrBlank()) {
            return text.trim()
        }

        val err = root.path("error").path("message").asText(null)
        if (!err.isNullOrBlank()) {
            error(err)
        }

        error("无法解析 AI 响应，请检查 api-url 与鉴权配置")
    }

    /**
     * 流式 AI 解析（OpenAI 兼容 SSE）。
     *
     * @param override 请求级 AI 配置（前端「AI 设置」），优先级高于配置文件默认值
     */
    fun parseStream(
        content: String,
        systemPrompt: String? = null,
        override: AiRuntimeConfig? = null,
        onDelta: (String) -> Unit,
    ) {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "内容不能为空" }
        val baseUrl = resolveBaseUrl(override)
        require(baseUrl.isNotBlank()) { "未配置 opinionflow.ai.api-url" }

        val sys = systemPrompt?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "你是舆情与新闻分析助手。请阅读用户给出的正文，输出：要点摘要、情绪/立场倾向（如有）、关键词与可跟进建议。使用中文，条理清晰。"

        val body = objectMapper.createObjectNode().apply {
            put("model", resolveModel(override))
            put("stream", true)
            set<JsonNode>(
                "messages",
                objectMapper.createArrayNode().apply {
                    add(
                        objectMapper.createObjectNode().apply {
                            put("role", "system")
                            put("content", sys)
                        },
                    )
                    add(
                        objectMapper.createObjectNode().apply {
                            put("role", "user")
                            put("content", trimmed)
                        },
                    )
                },
            )
        }

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(AiUrlNormalizer.chatCompletionsUrl(baseUrl)))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")

        val effectiveApiKey = resolveApiKey(override)
        if (effectiveApiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $effectiveApiKey")
        }

        val request = requestBuilder
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            val err = response.body().readAllBytes().toString(StandardCharsets.UTF_8)
            error("AI 接口错误：HTTP ${response.statusCode()} $err")
        }

        BufferedReader(InputStreamReader(response.body(), StandardCharsets.UTF_8)).use { br ->
            while (true) {
                val line = br.readLine() ?: break
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue

                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                if (data.isBlank()) continue

                val root = objectMapper.readTree(data)
                val delta = root.path("choices").path(0).path("delta").path("content").asText(null)
                if (!delta.isNullOrBlank()) {
                    onDelta(delta)
                    continue
                }

                val full = root.path("choices").path(0).path("message").path("content").asText(null)
                if (!full.isNullOrBlank()) {
                    onDelta(full)
                }
            }
        }
    }

    /**
     * 生效 baseUrl：请求级覆盖 > 配置文件默认，并统一归一化（自动补 /v1 等）。
     */
    private fun resolveBaseUrl(override: AiRuntimeConfig?): String =
        AiUrlNormalizer.normalize(override?.baseUrl ?: apiUrl)

    /**
     * 生效 api-key：请求级覆盖 > 配置文件默认。
     * 前端传入的用户 token 优先级高于 key.properties / application.yml 中的默认 token。
     */
    private fun resolveApiKey(override: AiRuntimeConfig?): String =
        override?.apiKey?.trim().takeUnless { it.isNullOrEmpty() } ?: apiKey.trim()

    /** 生效模型名：请求级覆盖 > 配置文件默认 */
    private fun resolveModel(override: AiRuntimeConfig?): String =
        override?.model?.trim().takeUnless { it.isNullOrEmpty() } ?: model
}