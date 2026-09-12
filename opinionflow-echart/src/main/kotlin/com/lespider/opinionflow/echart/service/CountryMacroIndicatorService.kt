package com.lespider.opinionflow.echart.service

import com.lespider.opinionflow.echart.domain.CountryMacroIndicator
import com.lespider.opinionflow.echart.repo.CountryMacroIndicatorRepository
import java.math.BigDecimal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 国家宏观指标服务
 * 世界格局地图点击国家后，按 country（或 countryCode）查询 spider 库 country_macro_indicators 表
 */
@Service
class CountryMacroIndicatorService(
    private val repository: CountryMacroIndicatorRepository,
) {

    /**
     * 按国家查询全部年度数据（按年份降序）。
     * 优先按 country 名称匹配，匹配不到时尝试按 countryCode 匹配。
     */
    @Transactional(readOnly = true)
    fun findByCountry(country: String): List<Map<String, Any?>> {
        val name = country.trim()
        if (name.isEmpty()) return emptyList()

        val byName = repository.findByCountryIgnoreCaseOrderByYearDesc(name)
        if (byName.isNotEmpty()) return byName.map { toMap(it) }

        return repository.findByCountryCodeIgnoreCaseOrderByYearDesc(name).map { toMap(it) }
    }

    /**
     * 按国家查询最近一年的数据（地图点击弹出表单展示推荐）。
     * 优先按 country 名称匹配，匹配不到时尝试按 countryCode 匹配。
     */
    @Transactional(readOnly = true)
    fun findLatest(country: String): Map<String, Any?>? {
        val name = country.trim()
        if (name.isEmpty()) return null

        return repository.findFirstByCountryIgnoreCaseOrderByYearDesc(name)?.let { toMap(it) }
            ?: repository.findFirstByCountryCodeIgnoreCaseOrderByYearDesc(name)?.let { toMap(it) }
    }

    /** 查询所有国家宏观指标（全部年份，供调试/管理） */
    @Transactional(readOnly = true)
    fun listAll(): List<Map<String, Any?>> = repository.findAll().map { toMap(it) }

    /**
     * 按国家 + 年份查询（同一国家同年可能存在多个数据源记录，均返回）。
     * 优先按 country 名称匹配，匹配不到时尝试按 countryCode 匹配。
     */
    @Transactional(readOnly = true)
    fun findByCountryAndYear(country: String, year: Int): List<Map<String, Any?>> {
        val name = country.trim()
        if (name.isEmpty()) return emptyList()

        val byName = repository.findByCountryIgnoreCaseAndYear(name, year)
        if (byName.isNotEmpty()) return byName.map { toMap(it) }

        return repository.findByCountryCodeIgnoreCaseAndYear(name, year).map { toMap(it) }
    }

    /**
     * 按 id 更新指定记录。
     * body 中出现的字段才更新（null / 空串视为清空该字段），未出现的字段保持原值。
     */
    @Transactional
    fun update(id: Long, body: Map<String, Any?>): Map<String, Any?>? {
        val existing = repository.findById(id).orElse(null) ?: return null

        val newCountry = txt(body, "country", existing.country)
        if (newCountry.isNullOrBlank()) throw IllegalArgumentException("country 不能为空")
        val newYear = when {
            body.containsKey("year") -> (body["year"] as? Number)?.toInt()
                ?: body["year"]?.toString()?.trim()?.toIntOrNull()
                ?: existing.year
            else -> existing.year
        }
        if (newYear <= 0) throw IllegalArgumentException("year 必须为正整数")

        val entity = existing.copy(
            country = newCountry,
            countryCode = txt(body, "countryCode", existing.countryCode),
            year = newYear,
            region = txt(body, "region", existing.region),
            // 经济总量
            gdpUsd = big(body, "gdpUsd", existing.gdpUsd),
            gdpGrowthPct = big(body, "gdpGrowthPct", existing.gdpGrowthPct),
            // 物价
            cpiPct = big(body, "cpiPct", existing.cpiPct),
            ppiPct = big(body, "ppiPct", existing.ppiPct),
            inflationPct = big(body, "inflationPct", existing.inflationPct),
            // 就业
            employmentRatePct = big(body, "employmentRatePct", existing.employmentRatePct),
            unemploymentRatePct = big(body, "unemploymentRatePct", existing.unemploymentRatePct),
            // 汇率
            exchangeRateUsd = big(body, "exchangeRateUsd", existing.exchangeRateUsd),
            // 人口
            populationTotal = big(body, "populationTotal", existing.populationTotal),
            populationGrowthPct = big(body, "populationGrowthPct", existing.populationGrowthPct),
            medianAge = big(body, "medianAge", existing.medianAge),
            urbanPopulationPct = big(body, "urbanPopulationPct", existing.urbanPopulationPct),
            age014Pct = big(body, "age014Pct", existing.age014Pct),
            age1564Pct = big(body, "age1564Pct", existing.age1564Pct),
            age65PlusPct = big(body, "age65PlusPct", existing.age65PlusPct),
            // 贸易
            exportsGoodsUsd = big(body, "exportsGoodsUsd", existing.exportsGoodsUsd),
            importsGoodsUsd = big(body, "importsGoodsUsd", existing.importsGoodsUsd),
            tradeOpennessPct = big(body, "tradeOpennessPct", existing.tradeOpennessPct),
            importExportRatio = big(body, "importExportRatio", existing.importExportRatio),
            // 政府负债
            govtDebtTotal = big(body, "govtDebtTotal", existing.govtDebtTotal),
            govtDebtGdpPct = big(body, "govtDebtGdpPct", existing.govtDebtGdpPct),
            govtDebtScope = txt(body, "govtDebtScope", existing.govtDebtScope),
            // 政治制度
            politicalSystem = txt(body, "politicalSystem", existing.politicalSystem),
            regimeType = txt(body, "regimeType", existing.regimeType),
            // 元数据
            dataSource = txt(body, "dataSource", existing.dataSource),
            sourceUrl = txt(body, "sourceUrl", existing.sourceUrl),
            notes = txt(body, "notes", existing.notes),
            updatedAt = java.time.LocalDateTime.now(),
        )
        return toMap(repository.save(entity))
    }

    /**
     * 查询有数据的国家清单（用于前端联想/校验）。
     * 返回以最近记录为准的 country / countryCode / region 基本信息。
     */
    @Transactional(readOnly = true)
    fun listCountries(): List<Map<String, Any?>> {
        // 简单的内存去重：按 country 名称为基准，取该国家最新一条记录的基础信息
        val byName = mutableMapOf<String, CountryMacroIndicator>()
        repository.findAllByOrderByYearDesc().forEach { e ->
            byName.putIfAbsent(e.country, e)
        }
        return byName.values.map {
            linkedMapOf<String, Any?>(
                "country" to it.country,
                "countryCode" to it.countryCode,
                "region" to it.region,
                "latestYear" to it.year,
            )
        }
    }

    // ========== 私有工具 ==========

    private fun toMap(e: CountryMacroIndicator): Map<String, Any?> = linkedMapOf(
        "id" to (e.id ?: 0L),
        "country" to e.country,
        "countryCode" to e.countryCode,
        "year" to e.year,
        "region" to e.region,
        // 经济总量
        "gdpUsd" to plain(e.gdpUsd),
        "gdpGrowthPct" to plain(e.gdpGrowthPct),
        // 物价
        "cpiPct" to plain(e.cpiPct),
        "ppiPct" to plain(e.ppiPct),
        "inflationPct" to plain(e.inflationPct),
        // 就业
        "employmentRatePct" to plain(e.employmentRatePct),
        "unemploymentRatePct" to plain(e.unemploymentRatePct),
        // 汇率
        "exchangeRateUsd" to plain(e.exchangeRateUsd),
        // 人口
        "populationTotal" to plain(e.populationTotal),
        "populationGrowthPct" to plain(e.populationGrowthPct),
        "medianAge" to plain(e.medianAge),
        "urbanPopulationPct" to plain(e.urbanPopulationPct),
        "age014Pct" to plain(e.age014Pct),
        "age1564Pct" to plain(e.age1564Pct),
        "age65PlusPct" to plain(e.age65PlusPct),
        // 贸易
        "exportsGoodsUsd" to plain(e.exportsGoodsUsd),
        "importsGoodsUsd" to plain(e.importsGoodsUsd),
        "tradeOpennessPct" to plain(e.tradeOpennessPct),
        "importExportRatio" to plain(e.importExportRatio),
        // 政府负债
        "govtDebtTotal" to plain(e.govtDebtTotal),
        "govtDebtGdpPct" to plain(e.govtDebtGdpPct),
        "govtDebtScope" to e.govtDebtScope,
        // 政治制度
        "politicalSystem" to e.politicalSystem,
        "regimeType" to e.regimeType,
        // 元数据
        "dataSource" to e.dataSource,
        "sourceUrl" to e.sourceUrl,
        "notes" to e.notes,
        "createdAt" to e.createdAt.toString(),
        "updatedAt" to e.updatedAt.toString(),
    )

    /** BigDecimal 转字符串，便于前端展示，null 保持 null */
    private fun plain(v: BigDecimal?): String? = v?.toPlainString()

    /** body 中出现则更新（null/空串→null 清空该字段），未出现保持原值 */
    private fun txt(body: Map<String, Any?>, key: String, current: String?): String? =
        if (body.containsKey(key)) strOrNull(body[key]) else current

    /** body 中出现则更新（null/空串→null 清空该字段），未出现保持原值 */
    private fun big(body: Map<String, Any?>, key: String, current: BigDecimal?): BigDecimal? =
        if (body.containsKey(key)) toBigDecimal(body[key]) else current

    private fun strOrNull(v: Any?): String? = when (v) {
        null -> null
        is String -> v.trim().takeIf { it.isNotEmpty() }
        else -> v.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun toBigDecimal(v: Any?): BigDecimal? = when (v) {
        is BigDecimal -> v
        is Number -> BigDecimal(v.toString())
        is String -> v.trim().toBigDecimalOrNull()
        else -> null
    }
}