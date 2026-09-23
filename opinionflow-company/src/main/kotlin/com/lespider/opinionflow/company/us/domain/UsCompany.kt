package com.lespider.opinionflow.company.us.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 美股公司主表实体（company_us 库）
 * 对应数据库表 company
 *
 * 说明：美股库主键列为 ticker（股票代码），接口层统一映射为前端使用的 companyCode 字段，
 *      以保证「美国企业」页面与「中国企业」页面数据结构完全一致。
 */
@Entity
@Table(name = "company")
data class UsCompany(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    /** 股票代码，如 AAPL / MSFT（对应中国库的 company_code） */
    @Column(name = "ticker", nullable = false, length = 20)
    val ticker: String,

    @Column(name = "company_name", nullable = false, length = 300)
    val companyName: String,

    @Column(name = "short_name", length = 150)
    val shortName: String? = null,

    /** 交易所：NYSE / NASDAQ / AMEX / ARCA */
    @Column(name = "exchange", length = 20)
    val exchange: String? = null,

    /** 行业板块(GICS)，如 Technology */
    @Column(name = "sector", length = 100)
    val sector: String? = null,

    /** 细分行业 */
    @Column(name = "industry", length = 100)
    val industry: String? = null,

    /** SEC CIK 编号 */
    @Column(name = "cik", length = 20)
    val cik: String? = null,

    /** 国际证券识别码 */
    @Column(name = "isin", length = 20)
    val isin: String? = null,

    @Column(name = "fiscal_year_end", length = 10)
    val fiscalYearEnd: String? = null,

    @Column(name = "country", length = 50)
    val country: String? = null,

    @Column(name = "currency", length = 10)
    val currency: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime? = null,
)
