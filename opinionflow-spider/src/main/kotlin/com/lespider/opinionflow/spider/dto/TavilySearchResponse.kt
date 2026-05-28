package com.lespider.opinionflow.spider.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class TavilySearchResponse(
    val query: String? = null,
    @JsonProperty("follow_up_questions")
    val followUpQuestions: List<String>? = null,
    val answer: String? = null,
    val images: List<TavilyImage>? = null,
    val results: List<TavilyResult>? = null,
    @JsonProperty("response_time")
    val responseTime: Double? = null,
    @JsonProperty("request_id")
    val requestId: String? = null,
)

data class TavilyResult(
    val url: String? = null,
    val title: String? = null,
    val content: String? = null,
    val score: Double? = null,
    @JsonProperty("raw_content")
    val rawContent: String? = null,
    val favicon: String? = null,
)

data class TavilyImage(
    val url: String? = null,
    val description: String? = null,
)