package com.lespider.opinionflow.spider.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "search_results")
class SearchResult(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @field:Column(columnDefinition = "TEXT")
    var query: String? = null,

    @field:Column(columnDefinition = "TEXT", nullable = false)
    var url: String = "",

    @field:Column(columnDefinition = "TEXT")
    var title: String? = null,

    @field:Column(precision = 10, scale = 8)
    var score: BigDecimal? = null,

    @field:Column(name = "published_date")
    var publishedDate: LocalDateTime? = null,

    @field:Column(columnDefinition = "TEXT")
    var content: String? = null,

    @field:Column(name = "raw_content", columnDefinition = "TEXT")
    var rawContent: String? = null,

    @field:Column(name = "created_at")
    var createdAt: LocalDateTime? = null,
)