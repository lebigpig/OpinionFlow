package com.lespider.opinionflow.ai.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Tavily 网络搜索服务
 * 调用 Tavily Search API 搜索互联网内容，为 AI 提供实时外部资料
 */
@Service
class TavilyWebSearchService(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${opinionflow.tavily.enabled:false}")
    private var enabled: Boolean = false

    @Value("\${opinionflow.tavily.api-key:}")
    private var apiKey: String = ""

    @Value("\${opinionflow.tavily.base-url:https://api.tavily.com}")
    private var baseUrl: String = "https://api.tavily.com"

    @Value("\${opinionflow.tavily.max-results:5}")
    private var maxResults: Int = 5

    @Value("\${opinionflow.tavily.search-depth:basic}")
    private var searchDepth: String = "basic"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .build()

    /**
     * 搜索互联网内容
     * @param query 搜索关键词
     * @return 格式化的搜索结果文本，供 AI 参考
     */
    fun search(query: String): String {
        if (!enabled || apiKey.isBlank()) {
            log.warn("Tavily 搜索未启用或 API Key 未配置，跳过联网搜索")
            return ""
        }

        return try {
            log.info("Tavily 搜索: query='{}', maxResults={}, searchDepth={}", query, maxResults, searchDepth)

            val requestBody = objectMapper.writeValueAsString(mapOf(
                "api_key" to apiKey,
                "query" to query,
                "max_results" to maxResults,
                "search_depth" to searchDepth,
                "include_answer" to true,
                "include_raw_content" to false,
            ))

            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(java.time.Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                log.error("Tavily 搜索失败: status={}, body={}", response.statusCode(), response.body())
                return ""
            }

            val json = objectMapper.readTree(response.body())
            formatSearchResults(json)
        } catch (e: Exception) {
            log.error("Tavily 搜索异常: {}", e.message, e)
            ""
        }
    }

    /**
     * 将 Tavily 返回的 JSON 格式化为 AI 可读的文本
     */
    private fun formatSearchResults(json: JsonNode): String {
        val sb = StringBuilder()

        // Tavily 返回的 answer 字段（AI 摘要）
        val answer = json.get("answer")?.asText()
        if (!answer.isNullOrBlank()) {
            sb.appendLine("【搜索摘要】$answer")
            sb.appendLine()
        }

        // results 数组
        val results = json.get("results")
        if (results != null && results.isArray && results.size() > 0) {
            sb.appendLine("【搜索结果】")
            results.forEachIndexed { index, item ->
                val title = item.get("title")?.asText() ?: "无标题"
                val url = item.get("url")?.asText() ?: ""
                val content = item.get("content")?.asText() ?: ""
                val score = item.get("score")?.asDouble() ?: 0.0

                sb.appendLine("${index + 1}. [$title]($url) (相关度: ${String.format("%.2f", score)})")
                // 截取前 500 字符避免 token 过长
                val truncatedContent = if (content.length > 500) content.take(500) + "..." else content
                sb.appendLine("   $truncatedContent")
                sb.appendLine()
            }
        }

        val result = sb.toString().trim()
        log.info("Tavily 搜索完成，返回 {} 条结果，共 {} 字符", results?.size() ?: 0, result.length)
        return result
    }
}