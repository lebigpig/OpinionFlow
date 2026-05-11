package com.lespider.opinionflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "yahoo_finance_news")
class YahooFinanceNews(
    @Id
    @Column(name = "id")
    var id: String? = null,

    @field:Column(name = "title")
    var title: String? = null,

    @field:Column(name = "summary")
    var summary: String? = null,

    @field:Column(name = "display_time")
    var displayTime: String? = null,

    @field:Column(name = "article_url")
    var articleUrl: String? = null,

    @field:Column(name = "img_url")
    var imgUrl: String? = null,

    @field:Column(name = "fetched_at")
    var fetchedAt: LocalDateTime? = null,
)

