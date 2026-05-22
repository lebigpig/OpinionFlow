package com.lespider.opinionflow.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(name = "falsh_news")
class FalshNews {
    @Id
    var id: Long? = null

    @Column(length = 1024)
    var send: String? = null

    @Lob
    var content: String? = null
}

