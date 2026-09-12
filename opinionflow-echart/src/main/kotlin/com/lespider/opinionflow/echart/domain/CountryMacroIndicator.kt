package com.lespider.opinionflow.echart.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 国家宏观指标实体
 * 对应数据库表 country_macro_indicators
 * 用于世界格局地图：点击国家后弹出表单，按 country 查询该国宏观指标
 */
@Entity
@Table(
    name = "country_macro_indicators",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_country_year_source", columnNames = ["country", "year", "data_source"])
    ]
)
data class CountryMacroIndicator(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "country", nullable = false, length = 100)
    val country: String,

    @Column(name = "country_code", length = 3)
    val countryCode: String? = null,

    @Column(name = "year", nullable = false)
    val year: Int,

    @Column(name = "region", length = 100)
    val region: String? = null,

    // ========== 经济总量 ==========
    @Column(name = "gdp_usd", precision = 20, scale = 2)
    val gdpUsd: BigDecimal? = null,

    @Column(name = "gdp_growth_pct", precision = 10, scale = 4)
    val gdpGrowthPct: BigDecimal? = null,

    // ========== 物价 ==========
    @Column(name = "cpi_pct", precision = 10, scale = 4)
    val cpiPct: BigDecimal? = null,

    @Column(name = "ppi_pct", precision = 10, scale = 4)
    val ppiPct: BigDecimal? = null,

    @Column(name = "inflation_pct", precision = 10, scale = 4)
    val inflationPct: BigDecimal? = null,

    // ========== 就业 ==========
    @Column(name = "employment_rate_pct", precision = 10, scale = 4)
    val employmentRatePct: BigDecimal? = null,

    @Column(name = "unemployment_rate_pct", precision = 10, scale = 4)
    val unemploymentRatePct: BigDecimal? = null,

    // ========== 汇率 ==========
    @Column(name = "exchange_rate_usd", precision = 20, scale = 6)
    val exchangeRateUsd: BigDecimal? = null,

    // ========== 人口 ==========
    @Column(name = "population_total", precision = 20, scale = 0)
    val populationTotal: BigDecimal? = null,

    @Column(name = "population_growth_pct", precision = 10, scale = 4)
    val populationGrowthPct: BigDecimal? = null,

    @Column(name = "median_age", precision = 6, scale = 2)
    val medianAge: BigDecimal? = null,

    @Column(name = "urban_population_pct", precision = 10, scale = 4)
    val urbanPopulationPct: BigDecimal? = null,

    @Column(name = "age_0_14_pct", precision = 10, scale = 4)
    val age014Pct: BigDecimal? = null,

    @Column(name = "age_15_64_pct", precision = 10, scale = 4)
    val age1564Pct: BigDecimal? = null,

    @Column(name = "age_65_plus_pct", precision = 10, scale = 4)
    val age65PlusPct: BigDecimal? = null,

    // ========== 贸易 ==========
    @Column(name = "exports_goods_usd", precision = 20, scale = 2)
    val exportsGoodsUsd: BigDecimal? = null,

    @Column(name = "imports_goods_usd", precision = 20, scale = 2)
    val importsGoodsUsd: BigDecimal? = null,

    @Column(name = "trade_openness_pct", precision = 10, scale = 4)
    val tradeOpennessPct: BigDecimal? = null,

    @Column(name = "import_export_ratio", precision = 10, scale = 4)
    val importExportRatio: BigDecimal? = null,

    // ========== 政府负债 ==========
    @Column(name = "govt_debt_total", precision = 20, scale = 2)
    val govtDebtTotal: BigDecimal? = null,

    @Column(name = "govt_debt_gdp_pct", precision = 10, scale = 4)
    val govtDebtGdpPct: BigDecimal? = null,

    @Column(name = "govt_debt_scope", length = 200)
    val govtDebtScope: String? = null,

    // ========== 政治制度 ==========
    @Column(name = "political_system", length = 200)
    val politicalSystem: String? = null,

    @Column(name = "regime_type", length = 100)
    val regimeType: String? = null,

    // ========== 元数据 ==========
    @Column(name = "data_source", length = 200)
    val dataSource: String? = null,

    @Column(name = "source_url", length = 500)
    val sourceUrl: String? = null,

    @Column(name = "notes", columnDefinition = "TEXT")
    val notes: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)