package com.lespider.opinionflow.news.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "yahoo_finance_news")
class YahooFinanceNews {
    @Id
    @Column(name = "id", length = 128)
    var id: String? = null

    @field:Column(name = "title", length = 1024)
    var title: String? = null

    @field:Column(name = "link", length = 2048)
    var link: String? = null

    var publisher: String? = null

    @field:Column(name = "published_date")
    var publishedDate: String? = null

    @field:Column(name = "summary", columnDefinition = "TEXT")
    var summary: String? = null

    @field:Column(name = "display_time")
    var displayTime: String? = null

    @field:Column(name = "article_url", length = 2048)
    var articleUrl: String? = null

    @field:Column(name = "img_url", length = 2048)
    var imgUrl: String? = null

    @field:Column(name = "fetched_at")
    var fetchedAt: java.time.LocalDateTime? = null
}
