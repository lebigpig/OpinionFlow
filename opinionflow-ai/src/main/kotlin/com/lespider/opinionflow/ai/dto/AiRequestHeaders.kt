package com.lespider.opinionflow.ai.dto

/**
 * 前端「AI 设置」→ 后端的覆盖配置请求头名。
 *
 * 用请求头而非请求体传递的原因：
 *   - /api/ai/parse、/api/ai/parse/stream、/api/chat-memory/chat、/api/ai/models 都能统一携带，
 *     不必逐个修改各接口的请求体结构（对既有调用方零影响）；
 *   - 未携带时后端完全走原有默认配置，旧前端行为不变。
 *
 * 网关（opinionflow-gateway）与 opinionflow-common 的 CORS 均放行自定义请求头，无需额外配置。
 */
object AiRequestHeaders {
    const val PROVIDER = "X-AI-Provider"
    const val BASE_URL = "X-AI-Base-Url"
    const val API_KEY = "X-AI-Api-Key"
    const val MODEL = "X-AI-Model"

    /** 组装请求级配置；全部为空时返回 null（表示沿用服务端默认） */
    fun toConfig(
        provider: String? = null,
        baseUrl: String? = null,
        apiKey: String? = null,
        model: String? = null,
    ): AiRuntimeConfig? {
        val config = AiRuntimeConfig(
            provider = provider?.trim()?.takeUnless { it.isEmpty() },
            baseUrl = baseUrl?.trim()?.takeUnless { it.isEmpty() },
            apiKey = apiKey?.trim()?.takeUnless { it.isEmpty() },
            model = model?.trim()?.takeUnless { it.isEmpty() },
        )
        return config.takeUnless { it.isBlank() }
    }
}
