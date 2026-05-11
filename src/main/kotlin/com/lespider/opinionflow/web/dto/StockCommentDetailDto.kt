package com.lespider.opinionflow.web.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class StockCommentDetailDto(
    val id: Long,
    val stockCode: String?,
    val analysisTime: LocalDateTime?,
    @JsonProperty("total_comments_analyzed")
    val totalCommentsAnalyzed: Long? = null,
    val mood: String? = null,
    val ivi: String? = null,
    @JsonProperty("narrative_coherence")
    val narrativeCoherence: String? = null,
    @JsonProperty("main_themes")
    val mainThemes: Map<String, Double> = emptyMap(),
    @JsonProperty("main_themes_content")
    val mainThemesContent: String? = null,
    @JsonProperty("theme_count")
    val themeCount: Int? = null,
    @JsonProperty("info_source_reliance")
    val infoSourceReliance: String? = null,
)
