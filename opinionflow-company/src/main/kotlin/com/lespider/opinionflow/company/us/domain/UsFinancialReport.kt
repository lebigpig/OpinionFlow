package com.lespider.opinionflow.company.us.domain

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 美股财报主表实体（company_us 库）
 * 对应数据库表 financial_report
 *
 * 三张报表（利润表 / 资产负债表 / 现金流量表）与财务指标均通过 report_id 关联本表。
 * 美股库额外提供 SEC 表单信息（form_type / accession_no / filing_url）与单位换算因子 scale_factor。
 */
@Entity
@Table(name = "financial_report")
data class UsFinancialReport(
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

    /** SEC 表单类型：10-K / 10-Q / 8-K / 20-F */
    @Column(name = "form_type", length = 20)
    val formType: String? = null,

    @Column(name = "report_date")
    val reportDate: LocalDate? = null,

    @Column(name = "publish_date")
    val publishDate: LocalDate? = null,

    /** SEC EDGAR 接入号 */
    @Column(name = "accession_no", length = 30)
    val accessionNo: String? = null,

    /** SEC EDGAR 原始文件链接 */
    @Column(name = "filing_url", length = 500)
    val filingUrl: String? = null,

    @Column(name = "currency", length = 10)
    val currency: String? = null,

    /** 金额单位：USD / thousands / millions */
    @Column(name = "unit", length = 20)
    val unit: String? = null,

    /** 单位换算因子（转基础单位），如 1000 / 1000000 */
    @Column(name = "scale_factor")
    val scaleFactor: Int? = null,

    @Column(name = "audit_status", length = 30)
    val auditStatus: String? = null,

    @Column(name = "parse_status", length = 20)
    val parseStatus: String? = null,

    @Column(name = "parsed_at")
    val parsedAt: LocalDateTime? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime? = null,
)
