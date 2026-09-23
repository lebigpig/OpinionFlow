package com.lespider.opinionflow.company.us.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 美股财务指标值实体（company_us 库）
 * 对应数据库表 financial_indicator
 * yoyChange 为同比变化率（%），后端已计算存库
 */
@Entity
@Table(name = "financial_indicator")
data class UsFinancialIndicator(
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

    @Column(name = "indicator_name_en", length = 150)
    val indicatorNameEn: String? = null,

    @Column(name = "indicator_value", precision = 22, scale = 6)
    val indicatorValue: BigDecimal? = null,

    @Column(name = "value_previous", precision = 22, scale = 6)
    val valuePrevious: BigDecimal? = null,

    @Column(name = "yoy_change", precision = 12, scale = 4)
    val yoyChange: BigDecimal? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime? = null,
)
