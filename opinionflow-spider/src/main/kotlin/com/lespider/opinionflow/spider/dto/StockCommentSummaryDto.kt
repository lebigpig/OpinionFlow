package com.lespider.opinionflow.spider.dto

import java.time.LocalDateTime

data class StockCommentSummaryDto(
    val id: Long,
    val stockCode: String?,
    val analysisTime: LocalDateTime?,
    val totalCommentsAnalyzed: Long?,
)
