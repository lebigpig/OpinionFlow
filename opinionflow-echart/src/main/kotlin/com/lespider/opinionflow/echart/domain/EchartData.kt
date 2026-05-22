package com.lespider.opinionflow.echart.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Echart 图表数据实体
 * 对应数据库表 echart_data
 * content 字段存储完整的 JSON 报告数据
 */
@Entity
@Table(name = "echart_data")
data class EchartData(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "content", nullable = false, columnDefinition = "JSON")
    val content: String = "",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)