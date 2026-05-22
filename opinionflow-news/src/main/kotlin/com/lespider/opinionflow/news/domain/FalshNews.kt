package com.lespider.opinionflow.news.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "falsh_news")
class FalshNews {
    @Id
    var id: Long? = null

    @Column(length = 1024)
    var send: String? = null

    @Column(columnDefinition = "LONGTEXT")
    var content: String? = null
}