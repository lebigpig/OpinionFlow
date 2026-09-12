package com.lespider.opinionflow.company.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 现金流量表明细实体
 * 对应数据库表 cash_flow_statement
 */
@Entity
@Table(name = "cash_flow_statement")
data class CashFlowStatement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "report_id", nullable = false)
    val reportId: Long,

    @Column(name = "item_name", length = 200)
    val itemName: String? = null,

    @Column(name = "item_level")
    val itemLevel: Int? = null,

    @Column(name = "parent_item", length = 200)
    val parentItem: String? = null,

    @Column(name = "is_total")
    val isTotal: Int? = null,

    @Column(name = "is_sub_item")
    val isSubItem: Int? = null,

    /** 活动类型：经营 / 投资 / 筹资 */
    @Column(name = "activity_type", length = 20)
    val activityType: String? = null,

    @Column(name = "unit", length = 10)
    val unit: String? = null,

    @Column(name = "value_current", precision = 20, scale = 2)
    val valueCurrent: BigDecimal? = null,

    @Column(name = "value_previous", precision = 20, scale = 2)
    val valuePrevious: BigDecimal? = null,

    @Column(name = "note_ref", length = 50)
    val noteRef: String? = null,

    @Column(name = "sort_order")
    val sortOrder: Int? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime? = null,
)