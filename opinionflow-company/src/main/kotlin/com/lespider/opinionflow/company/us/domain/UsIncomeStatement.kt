package com.lespider.opinionflow.company.us.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 美股利润表明细实体（company_us 库）
 * 对应数据库表 income_statement
 *
 * 美股科目额外带 US GAAP taxonomy 编码（item_code）与英文名（item_name_en）。
 */
@Entity
@Table(name = "income_statement")
data class UsIncomeStatement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "report_id", nullable = false)
    val reportId: Long,

    /** 科目编码（US GAAP taxonomy tag），如 Revenues */
    @Column(name = "item_code", length = 80)
    val itemCode: String? = null,

    @Column(name = "item_name", length = 200)
    val itemName: String? = null,

    @Column(name = "item_name_en", length = 200)
    val itemNameEn: String? = null,

    @Column(name = "item_level")
    val itemLevel: Int? = null,

    @Column(name = "parent_item", length = 200)
    val parentItem: String? = null,

    @Column(name = "is_total")
    val isTotal: Int? = null,

    @Column(name = "is_sub_item")
    val isSubItem: Int? = null,

    @Column(name = "unit", length = 10)
    val unit: String? = null,

    @Column(name = "value_current", precision = 22, scale = 2)
    val valueCurrent: BigDecimal? = null,

    @Column(name = "value_previous", precision = 22, scale = 2)
    val valuePrevious: BigDecimal? = null,

    @Column(name = "sort_order")
    val sortOrder: Int? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime? = null,
)
