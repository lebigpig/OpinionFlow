package com.lespider.opinionflow.company.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 财务指标值实体
 * 对应数据库表 financial_indicator_value
 * yoyChange 为同比增长率（后端已计算存库）
 */
@Entity
@Table(name = "financial_indicator_value")
data class FinancialIndicatorValue(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "report_id", nullable = false)
    val reportId: Long,

    @Column(name = "indicator_code", length = 50)
    val indicatorCode: String? = null,

    @Column(name = "indicator_name", length = 100)
    val indicatorName: String? = null,

    @Column(name = "indicator_value", precision = 20, scale = 4)
    val indicatorValue: BigDecimal? = null,

    @Column(name = "value_previous", precision = 20, scale = 4)
    val valuePrevious: BigDecimal? = null,

    @Column(name = "yoy_change", precision = 10, scale = 4)
    val yoyChange: BigDecimal? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime? = null,
)