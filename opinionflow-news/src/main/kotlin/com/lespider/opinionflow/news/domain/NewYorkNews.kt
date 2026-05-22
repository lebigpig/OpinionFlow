package com.lespider.opinionflow.news.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "new_york_news")
class NewYorkNews(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @field:Column(name = "title")
    var title: String? = null,

    @field:Column(name = "summary", columnDefinition = "TEXT")
    var summary: String? = null,

    @field:Column(name = "link")
    var link: String? = null,

    @field:Column(name = "img")
    var img: String? = null,

    @field:Column(name = "publish_date")
    var publishDate: String? = null,
)
