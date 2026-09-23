package com.lespider.opinionflow.ai.service

/**
 * OpenAI 兼容接口地址归一化工具。
 *
 * 前端「AI 设置」弹窗与配置文件里的 baseUrl 写法五花八门：
 *   https://api.deepseek.com                       （DeepSeek 官方写法，无 /v1）
 *   https://api.deepseek.com/v1
 *   https://api.deepseek.com/v1/chat/completions   （直接粘贴完整接口地址）
 *
 * 本工具统一归一为「不含具体接口路径、路径部分保留」的 baseUrl，规则：
 *   1. 去掉末尾 `/` 与 `/chat/completions`、`/completions`、`/models`、`/embeddings` 后缀
 *   2. 若去掉后没有路径（如 `https://api.deepseek.com`）→ 补 `/v1`；
 *      若已有路径（如 `/v1`、`/compatible-mode/v1`、`/api/paas/v4`）→ 原样保留
 *
 * 这样 LangChain4j 的 baseUrl、原生 HTTP 的 /chat/completions 与 /models
 * 都由同一份归一化结果拼接，保证两种调用方式对同一输入得到一致的接口地址。
 *
 * 例：
 *   https://api.deepseek.com                       → https://api.deepseek.com/v1
 *   https://api.deepseek.com/v1                    → https://api.deepseek.com/v1
 *   https://api.deepseek.com/v1/chat/completions   → https://api.deepseek.com/v1
 *   https://dashscope.aliyuncs.com/compatible-mode/v1 → 原样保留
 */
object AiUrlNormalizer {

    /** 需要从末尾剥离的接口路径（顺序敏感：长后缀优先） */
    private val TAIL_SEGMENTS = listOf("/chat/completions", "/completions", "/models", "/embeddings")

    /** 归一化 baseUrl；输入为空时返回空串 */
    fun normalize(baseUrl: String?): String {
        var url = baseUrl?.trim().orEmpty().removeSuffix("/")
        if (url.isEmpty()) return ""

        for (tail in TAIL_SEGMENTS) {
            if (url.endsWith(tail, ignoreCase = true)) {
                url = url.substring(0, url.length - tail.length).removeSuffix("/")
                break
            }
        }
        if (url.isEmpty()) return ""

        // 非法地址（无协议头）原样返回，交由调用方兜底
        val schemeIdx = url.indexOf("://")
        if (schemeIdx < 0) return url

        val pathStart = url.indexOf('/', schemeIdx + 3)
        val path = if (pathStart >= 0) url.substring(pathStart + 1).trim('/') else ""
        return if (path.isEmpty()) "$url/v1" else url
    }

    /** 归一化后的 /chat/completions 地址；输入为空时返回空串 */
    fun chatCompletionsUrl(baseUrl: String?): String {
        val base = normalize(baseUrl)
        return if (base.isEmpty()) "" else "$base/chat/completions"
    }

    /** 归一化后的 /models 地址（OpenAI 兼容「可调用模型清单」接口）；输入为空时返回空串 */
    fun modelsUrl(baseUrl: String?): String {
        val base = normalize(baseUrl)
        return if (base.isEmpty()) "" else "$base/models"
    }
}
