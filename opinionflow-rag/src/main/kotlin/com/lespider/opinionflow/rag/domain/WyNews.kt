package com.lespider.opinionflow.rag.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 网易财经新闻实体（RAG 服务本地副本，只读）
 */
@Entity
@Table(name = "wynews")
data class WyNews(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(length = 255)
    var title: String? = null,

    @Column(columnDefinition = "LONGTEXT")
    var content: String? = null,

    @Column(name = "`time`")
    var publishTime: LocalDateTime? = null,

    @Column(length = 255)
    var source: String? = null,

    @Column(length = 255)
    var url: String? = null,

    @Column(name = "img_url", length = 255)
    var imgUrl: String? = null,
)