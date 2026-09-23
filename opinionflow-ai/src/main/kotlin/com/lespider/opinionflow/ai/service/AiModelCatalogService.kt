package com.lespider.opinionflow.ai.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.lespider.opinionflow.ai.dto.AiModelDto
import com.lespider.opinionflow.ai.dto.AiModelsResponse
import com.lespider.opinionflow.ai.dto.AiRuntimeConfig
import com.lespider.opinionflow.ai.dto.AiServerDefaultDto
import com.lespider.opinionflow.ai.dto.AiSettingsResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service

/**
 * AI 配置目录服务（前端「AI 设置」弹窗的后端支撑）。
 *
 * 职责：
 * 1. 汇总「当前生效配置」与「服务端默认配置」，供前端展示（服务端默认 token 只回传脱敏值）；
 * 2. 以生效配置真实调用 OpenAI 兼容的 `{baseUrl}/models`，返回该 AI 服务可调用的模型清单，
 *    远端不可用（网络不通 / token 无效 / 该厂商不提供 /models）时回退预置兜底清单。
 *
 * 配置优先级：**请求级（前端设置） > 服务端默认（key.properties / application.yml）**。
 */
@Service
class AiModelCatalogService(
    private val objectMapper: ObjectMapper,
    private val presets: AiProviderPresets,
    @Value("\${opinionflow.ai.api-url:}") private val defaultApiUrl: String,
    @Value("\${opinionflow.ai.api-key:}") private val defaultApiKey: String,
    @Value("\${opinionflow.ai.model:gpt-4o-mini}") private val defaultModel: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    /** 解析后的生效配置 */
    private data class Effective(
        val provider: String?,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val keySource: String,
    )

    /** 生效配置：请求级覆盖 > 服务端默认 */
    private fun resolve(request: AiRuntimeConfig?): Effective {
        val requestKey = request?.apiKey?.trim().orEmpty()
        return Effective(
            provider = request?.provider?.trim().takeUnless { it.isNullOrEmpty() },
            baseUrl = AiUrlNormalizer.normalize(request?.baseUrl ?: defaultApiUrl),
            apiKey = if (requestKey.isNotEmpty()) requestKey else defaultApiKey.trim(),
            model = request?.model?.trim().takeUnless { it.isNullOrEmpty() } ?: defaultModel,
            keySource = when {
                requestKey.isNotEmpty() -> "request"
                defaultApiKey.isNotBlank() -> "server"
                else -> "none"
            },
        )
    }

    /** GET /api/ai/settings 的业务实现 */
    fun settings(request: AiRuntimeConfig?): AiSettingsResponse {
        val eff = resolve(request)
        return AiSettingsResponse(
            provider = eff.provider,
            baseUrl = eff.baseUrl,
            model = eff.model,
            keySource = eff.keySource,
            serverDefault = AiServerDefaultDto(
                configured = defaultApiUrl.isNotBlank() && defaultApiKey.isNotBlank(),
                provider = null,
                baseUrl = AiUrlNormalizer.normalize(defaultApiUrl),
                model = defaultModel,
                apiKeyMasked = maskApiKey(defaultApiKey),
            ),
            presets = presets.all,
        )
    }

    /** GET /api/ai/models 的业务实现 */
    fun models(request: AiRuntimeConfig?): AiModelsResponse {
        val eff = resolve(request)
        val modelsUrl = AiUrlNormalizer.modelsUrl(eff.baseUrl)
        if (modelsUrl.isEmpty()) {
            return fallback(eff, "未配置 AI 接口地址（opinionflow.ai.api-url 或前端 AI 设置）")
        }
        return try {
            val remote = fetchRemoteModels(modelsUrl, eff.apiKey)
            if (remote.isEmpty()) {
                fallback(eff, "接口返回的模型清单为空")
            } else {
                log.info("[AiModels] 拉取成功 url={} keySource={} 模型数={}", modelsUrl, eff.keySource, remote.size)
                AiModelsResponse(eff.provider, eff.baseUrl, eff.keySource, "remote", remote)
            }
        } catch (e: Exception) {
            log.warn("[AiModels] 拉取模型清单失败 url={} keySource={} err={}", modelsUrl, eff.keySource, e.message)
            fallback(eff, e.message ?: "拉取模型清单失败")
        }
    }

    /** 真实调用 {baseUrl}/models（OpenAI 兼容协议） */
    private fun fetchRemoteModels(modelsUrl: String, apiKey: String): List<AiModelDto> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(modelsUrl))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(20))
            .GET()
        if (apiKey.isNotBlank()) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
        }
        val response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            error("HTTP ${response.statusCode()} ${response.body().take(200)}")
        }
        return parseModels(response.body())
    }

    /**
     * 解析各家 /models 响应，兼容多种形态：
     *   { "data": [ { "id": "...", "owned_by": "..." } ] }   OpenAI / DeepSeek / 硅基流动
     *   { "models": [ { "name"/"id": "..." } ] }             部分网关
     *   [ "model-a", "model-b" ]                              纯字符串数组
     */
    private fun parseModels(body: String): List<AiModelDto> {
        val root = objectMapper.readTree(body)
        val array: JsonNode = when {
            root.isArray -> root
            root.path("data").isArray -> root.path("data")
            root.path("models").isArray -> root.path("models")
            root.path("result").isArray -> root.path("result")
            else -> return emptyList()
        }

        val list = mutableListOf<AiModelDto>()
        for (node in array) {
            if (node.isTextual) {
                val id = node.asText().trim()
                if (id.isNotEmpty()) list.add(AiModelDto(id = id))
                continue
            }
            val rawId = node.path("id").asText(null)
                ?: node.path("model").asText(null)
                ?: node.path("name").asText(null)
            val modelId = rawId?.trim().orEmpty()
            if (modelId.isEmpty()) continue
            list.add(
                AiModelDto(
                    id = modelId,
                    label = node.path("label").asText(null)?.trim()?.takeUnless { it.isEmpty() },
                    ownedBy = node.path("owned_by").asText(null)?.trim()?.takeUnless { it.isEmpty() },
                ),
            )
        }
        return list.distinctBy { it.id }.sortedBy { it.id }
    }

    /** 兜底响应：预置模型 + 当前配置的模型（保证前端始终有可勾选项） */
    private fun fallback(eff: Effective, error: String?): AiModelsResponse {
        val ids = LinkedHashSet<String>()
        ids.addAll(presets.fallbackModels(eff.provider))
        eff.model.takeIf { it.isNotBlank() }?.let { ids.add(it) }
        return AiModelsResponse(
            provider = eff.provider,
            baseUrl = eff.baseUrl,
            keySource = eff.keySource,
            source = "fallback",
            models = ids.map { AiModelDto(id = it) },
            error = error,
        )
    }

    /** token 脱敏：保留前 4 位与后 4 位，中间打码（服务端默认 token 绝不明文回传前端） */
    private fun maskApiKey(key: String): String {
        val k = key.trim()
        if (k.isEmpty()) return ""
        if (k.length <= 8) return "*".repeat(k.length)
        return "${k.take(4)}****${k.takeLast(4)}"
    }
}
