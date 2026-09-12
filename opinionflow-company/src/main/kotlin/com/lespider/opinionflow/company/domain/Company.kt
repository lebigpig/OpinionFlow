package com.lespider.opinionflow.company.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 公司主表实体
 * 对应数据库表 company
 */
@Entity
@Table(name = "company")
data class Company(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "company_code", nullable = false, length = 20)
    val companyCode: String,

    @Column(name = "company_name", nullable = false, length = 200)
    val companyName: String,

    @Column(name = "short_name", length = 100)
    val shortName: String? = null,

    @Column(name = "exchange", length = 20)
    val exchange: String? = null,

    @Column(name = "industry", length = 100)
    val industry: String? = null,

    @Column(name = "fiscal_year_end", length = 10)
    val fiscalYearEnd: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime? = null,
)