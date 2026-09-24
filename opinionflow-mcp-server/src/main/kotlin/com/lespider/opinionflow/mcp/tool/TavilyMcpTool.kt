package com.lespider.opinionflow.mcp.tool

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Tavily 联网搜索 MCP 工具。
 *
 * 从 opinionflow-ai 迁移而来（原 TavilyWebSearchService + TavilySearchTool）。
 * AI 通过 MCP Client（SSE）远程调用此工具做实时互联网搜索。
 */
@Component
class TavilyMcpTool(
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
     * 搜索互联网获取最新信息。
     * @param query 精炼的搜索关键词
     */
    @Tool(
        name = "webSearch",
        description = "搜索互联网获取最新信息。当你需要查找实时新闻、最新数据、市场行情、突发事件等无法从已有知识中获取的信息时，请调用此工具。参数 query 应为精炼的搜索关键词。",
    )
    fun webSearch(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) {
            return "搜索关键词不能为空。"
        }
        if (!enabled || apiKey.isBlank()) {
            log.warn("Tavily 搜索未启用或 API Key 未配置，跳过联网搜索")
            return "Tavily 联网搜索未启用或 API Key 未配置。"
        }
        log.info("[MCP Tool] Tavily 搜索: query='{}', maxResults={}, searchDepth={}", q, maxResults, searchDepth)
        return try {
            val requestBody = objectMapper.writeValueAsString(
                mapOf(
                    "api_key" to apiKey,
                    "query" to q,
                    "max_results" to maxResults,
                    "search_depth" to searchDepth,
                    "include_answer" to true,
                    "include_raw_content" to false,
                ),
            )
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(java.time.Duration.ofSeconds(30))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.error("Tavily 搜索失败: status={}, body={}", response.statusCode(), response.body().take(500))
                return "Tavily 搜索失败：HTTP ${response.statusCode()}"
            }
            val rawBody = response.body()
            val rawForLog = if (rawBody.length <= 2000) rawBody else rawBody.take(2000) + "...(共 ${rawBody.length} 字符，已截断)"
            log.info("[Tavily] HTTP 原始响应({} 字符): {}", rawBody.length, rawForLog)
            formatSearchResults(objectMapper.readTree(rawBody))
        } catch (e: Exception) {
            log.error("Tavily 搜索异常: {}", e.message, e)
            "Tavily 搜索异常：${e.message}"
        }
    }

    private fun formatSearchResults(json: JsonNode): String {
        val sb = StringBuilder()
        val answer = json.get("answer")?.asText()
        if (!answer.isNullOrBlank()) {
            sb.appendLine("【搜索摘要】$answer")
            sb.appendLine()
        }
        val results = json.get("results")
        if (results != null && results.isArray && results.size() > 0) {
            sb.appendLine("【搜索结果】")
            results.forEachIndexed { index, item ->
                val title = item.get("title")?.asText() ?: "无标题"
                val url = item.get("url")?.asText() ?: ""
                val content = item.get("content")?.asText() ?: ""
                val score = item.get("score")?.asDouble() ?: 0.0
                sb.appendLine("${index + 1}. [$title]($url) (相关度: ${String.format("%.2f", score)})")
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
