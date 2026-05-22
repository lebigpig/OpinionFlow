package com.lespider.opinionflow.rag.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 实时财经新闻实体（RAG 服务本地副本，只读）
 */
@Entity
@Table(name = "falsh_news")
data class FalshNews(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** 新闻发送方 / 来源，兼做标题用途 */
    @Column(name = "send", length = 255)
    var send: String? = null,

    @Column(columnDefinition = "LONGTEXT")
    var content: String? = null,

    @Column(name = "createtime")
    var createTime: LocalDateTime? = null,
)