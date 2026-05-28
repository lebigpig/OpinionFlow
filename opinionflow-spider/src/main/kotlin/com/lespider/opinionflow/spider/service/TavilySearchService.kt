package com.lespider.opinionflow.spider.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.lespider.opinionflow.spider.dto.TavilySearchRequest
import com.lespider.opinionflow.spider.dto.TavilySearchResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class TavilySearchService(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(TavilySearchService::class.java)

    @Value("\${tavily.api-key}")
    private lateinit var apiKey: String

    @Value("\${tavily.base-url:https://api.tavily.com}")
    private lateinit var baseUrl: String

    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl(baseUrl)
            .build()
    }

    fun search(req: TavilySearchRequest): TavilySearchResponse {
        val body = buildRequestBody(req)
        log.info("Tavily search request: query={}, topic={}, timeRange={}", req.query, req.topic, req.timeRange)

        val rawResponse = webClient.post()
            .uri("/search")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $apiKey")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
            ?: throw RuntimeException("Tavily API returned empty response")

        return objectMapper.readValue(rawResponse, TavilySearchResponse::class.java)
    }

    private fun buildRequestBody(req: TavilySearchRequest): Map<String, Any?> {
        val body = mutableMapOf<String, Any?>(
            "query" to req.query,
            "topic" to req.topic,
            "search_depth" to req.searchDepth,
            "max_results" to req.maxResults,
            "time_range" to req.timeRange,
            "include_answer" to req.includeAnswer,
            "include_images" to req.includeImages,
            "include_image_descriptions" to req.includeImageDescriptions,
            "include_favicon" to req.includeFavicon,
            "include_raw_content" to req.includeRawContent,
            "chunks_per_source" to req.chunksPerSource,
            "include_usage" to req.includeUsage,
        )
        // 可选的日期参数
        if (!req.startDate.isNullOrBlank()) body["start_date"] = req.startDate
        if (!req.endDate.isNullOrBlank()) body["end_date"] = req.endDate
        return body
    }
}