package com.lespider.opinionflow.ai.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val httpClient: HttpClient = HttpClient.newBuilder().build()

    fun parse(content: String, systemPrompt: String? = null): String {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "内容不能为空" }
        require(apiUrl.isNotBlank()) { "未配置 opinionflow.ai.api-url" }

        val sys = systemPrompt?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "你是舆情与新闻分析助手。请阅读用户给出的正文，输出：要点摘要、情绪/立场倾向（如有）、关键词与可跟进建议。使用中文，条理清晰。"

        val body = objectMapper.createObjectNode().apply {
            put("model", model)
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

        val spec = restClient.post()
            .uri(chatCompletionsUrl())
            .contentType(MediaType.APPLICATION_JSON)
            .headers { headers ->
                if (apiKey.isNotBlank()) {
                    headers.set(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                }
            }
            .body(body.toString())

        val response = spec.retrieve().body(String::class.java)
            ?: error("AI 接口返回空响应")

        val root = objectMapper.readTree(response)
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
     * DeepSeek(OpenAI兼容) 流式：每次回调一个增量片段（delta content）
     */
    fun parseStream(content: String, systemPrompt: String? = null, onDelta: (String) -> Unit) {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "内容不能为空" }
        require(apiUrl.isNotBlank()) { "未配置 opinionflow.ai.api-url" }

        val sys = systemPrompt?.trim().takeUnless { it.isNullOrEmpty() }
            ?: "你是舆情与新闻分析助手。请阅读用户给出的正文，输出：要点摘要、情绪/立场倾向（如有）、关键词与可跟进建议。使用中文，条理清晰。"

        val body = objectMapper.createObjectNode().apply {
            put("model", model)
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
            .uri(URI.create(chatCompletionsUrl()))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")

        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
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

    private fun chatCompletionsUrl(): String {
        val raw = apiUrl.trim()
        if (raw.isBlank()) return raw
        val normalized = raw.removeSuffix("/")
        if (normalized.contains("/chat/completions")) return normalized
        return "$normalized/v1/chat/completions"
    }
}