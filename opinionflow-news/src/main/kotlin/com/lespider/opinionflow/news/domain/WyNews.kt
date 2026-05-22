package com.lespider.opinionflow.news.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "wynews")
class WyNews(
    @Id
    @Column(name = "id")
    var id: Long? = null,

    @field:Column(name = "title")
    var title: String? = null,

    @field:Column(name = "content", columnDefinition = "LONGTEXT")
    var content: String? = null,

    @field:Column(name = "publish_time")
    var publishTime: LocalDateTime? = null,

    @field:Column(name = "source")
    var source: String? = null,

    @field:Column(name = "url")
    var url: String? = null,

    @field:Column(name = "time")
    var time: LocalDateTime? = null,

    @field:Column(name = "img_url")
    var imgUrl: String? = null,
)