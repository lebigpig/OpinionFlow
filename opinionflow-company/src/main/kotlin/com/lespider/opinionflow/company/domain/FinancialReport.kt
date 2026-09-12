package com.lespider.opinionflow.company.domain

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 财报主表实体
 * 对应数据库表 financial_report
 * 三张报表（利润表 / 资产负债表 / 现金流量表）与财务指标均通过 report_id 关联本表
 */
@Entity
@Table(name = "financial_report")
data class FinancialReport(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "company_id", nullable = false)
    val companyId: Long,

    @Column(name = "report_type", length = 20)
    val reportType: String? = null,

    @Column(name = "fiscal_year")
    val fiscalYear: Int? = null,

    @Column(name = "fiscal_period", length = 10)
    val fiscalPeriod: String? = null,

    @Column(name = "report_date")
    val reportDate: LocalDate? = null,

    @Column(name = "publish_date")
    val publishDate: LocalDate? = null,

    @Column(name = "currency", length = 10)
    val currency: String? = null,

    @Column(name = "unit", length = 20)
    val unit: String? = null,

    @Column(name = "audit_status", length = 20)
    val auditStatus: String? = null,

    @Column(name = "pdf_file_path", length = 500)
    val pdfFilePath: String? = null,

    @Column(name = "pdf_file_hash", length = 64)
    val pdfFileHash: String? = null,

    @Column(name = "parse_status", length = 20)
    val parseStatus: String? = null,

    @Column(name = "parsed_at")
    val parsedAt: LocalDateTime? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime? = null,
)