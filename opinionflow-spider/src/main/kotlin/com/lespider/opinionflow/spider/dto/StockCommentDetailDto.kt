package com.lespider.opinionflow.spider.dto

import java.time.LocalDateTime

data class StockCommentDetailDto(
    val id: Long,
    val stockCode: String?,
    val analysisTime: LocalDateTime?,
    val totalCommentsAnalyzed: Long?,
    val mood: String?,
    val ivi: String?,
    val narrativeCoherence: String?,
    val mainThemes: Map<String, Double>,
    val mainThemesContent: String?,
    val themeCount: Int?,
    val infoSourceReliance: String?,
)
