package com.lespider.opinionflow.web.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class StockCommentSummaryDto(
    val id: Long,
    val stockCode: String?,
    val analysisTime: LocalDateTime?,
    @JsonProperty("total_comments_analyzed")
    val totalCommentsAnalyzed: Long? = null,
)

