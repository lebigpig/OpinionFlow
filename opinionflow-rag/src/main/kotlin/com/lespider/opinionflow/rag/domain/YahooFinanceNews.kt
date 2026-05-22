package com.lespider.opinionflow.rag.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 雅虎财经新闻实体（RAG 服务本地副本，只读）
 */
@Entity
@Table(name = "yahoo_finance_news")
class YahooFinanceNews(
    @Id
    @Column(name = "id")
    var id: String? = null,

    @field:Column(name = "title", length = 1024)
    var title: String? = null,

    @field:Column(name = "summary", columnDefinition = "TEXT")
    var summary: String? = null,

    @field:Column(name = "display_time")
    var displayTime: String? = null,

    @field:Column(name = "article_url", length = 2048)
    var articleUrl: String? = null,

    @field:Column(name = "img_url", length = 2048)
    var imgUrl: String? = null,

    @field:Column(name = "fetched_at")
    var fetchedAt: LocalDateTime? = null,
)