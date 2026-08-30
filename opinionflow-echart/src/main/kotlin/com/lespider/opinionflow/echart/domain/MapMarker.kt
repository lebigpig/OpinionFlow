package com.lespider.opinionflow.echart.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 地图标记实体
 * 对应数据库表 map_markers，用于世界格局地图拖放图例后填写表单保存的标记信息
 */
@Entity
@Table(name = "map_markers")
data class MapMarker(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    val latitude: BigDecimal = BigDecimal.ZERO,

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    val longitude: BigDecimal = BigDecimal.ZERO,

    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @Column(name = "category", nullable = false, length = 50)
    val category: String,

    @Column(name = "icon_type", nullable = false, length = 30)
    val iconType: String,

    @Column(name = "country", length = 50)
    val country: String? = null,

    @Column(name = "region", length = 100)
    val region: String? = null,

    @Column(name = "description", columnDefinition = "TEXT")
    val description: String? = null,

    @Column(name = "annual_output", length = 50)
    val annualOutput: String? = null,

    @Column(name = "annual_profit", length = 50)
    val annualProfit: String? = null,

    @Column(name = "operator", length = 100)
    val operator: String? = null,

    @Column(name = "status")
    val status: Int = 1,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "created_by", length = 50)
    val createdBy: String? = null,
)
