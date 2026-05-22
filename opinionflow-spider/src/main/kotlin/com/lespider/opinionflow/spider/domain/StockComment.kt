package com.lespider.opinionflow.spider.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "stock_comment")
class StockComment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "stock_code")
    var stockCode: String? = null,

    @Column(name = "analysis_time")
    var analysisTime: LocalDateTime? = null,

    @field:Column(name = "total_comments_analyzed")
    var total: Long? = null,

    @field:Column(name = "mood")
    var mood: String? = null,

    @field:Column(name = "ivi")
    var ivi: String? = null,

    @field:Column(name = "narrative_coherence")
    var narrativeCoherence: String? = null,

    @field:JdbcTypeCode(SqlTypes.JSON)
    @field:Column(name = "main_themes")
    var mainThemesJson: String? = null,

    @field:Column(name = "main_themes_content", columnDefinition = "TEXT")
    var mainThemesContent: String? = null,

    @field:Column(name = "theme_count")
    var themeCount: Int? = null,

    @field:Column(name = "info_source_reliance")
    var infoSourceReliance: String? = null,
)