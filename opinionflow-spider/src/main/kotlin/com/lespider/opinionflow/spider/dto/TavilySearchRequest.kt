package com.lespider.opinionflow.spider.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class TavilySearchRequest(
    val query: String,
    val topic: String = "news",
    @JsonProperty("search_depth") val searchDepth: String = "advanced",
    @JsonProperty("max_results") val maxResults: Int = 9,
    @JsonProperty("time_range") val timeRange: String = "day",
    @JsonProperty("start_date") val startDate: String? = null,
    @JsonProperty("end_date") val endDate: String? = null,
    @JsonProperty("include_answer") val includeAnswer: Any = "basic",
    @JsonProperty("include_images") val includeImages: Boolean = true,
    @JsonProperty("include_image_descriptions") val includeImageDescriptions: Boolean = true,
    @JsonProperty("include_favicon") val includeFavicon: Boolean = true,
    @JsonProperty("include_raw_content") val includeRawContent: String = "text",
    @JsonProperty("chunks_per_source") val chunksPerSource: Int = 4,
    @JsonProperty("include_usage") val includeUsage: Boolean = true,
)
