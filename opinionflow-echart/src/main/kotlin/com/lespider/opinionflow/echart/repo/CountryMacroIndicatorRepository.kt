package com.lespider.opinionflow.echart.repo

import com.lespider.opinionflow.echart.domain.CountryMacroIndicator
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CountryMacroIndicatorRepository : JpaRepository<CountryMacroIndicator, Long> {

    /** 按国家名称（忽略大小写）查询，按年份降序 */
    fun findByCountryIgnoreCaseOrderByYearDesc(country: String): List<CountryMacroIndicator>

    /** 按 ISO 3166 alpha-3 国家代码（忽略大小写）查询，按年份降序 */
    fun findByCountryCodeIgnoreCaseOrderByYearDesc(countryCode: String): List<CountryMacroIndicator>

    /** 按国家名称查询最新一年数据 */
    fun findFirstByCountryIgnoreCaseOrderByYearDesc(country: String): CountryMacroIndicator?

    /** 按 ISO 3166 alpha-3 国家代码查询最新一年数据 */
    fun findFirstByCountryCodeIgnoreCaseOrderByYearDesc(countryCode: String): CountryMacroIndicator?

    /** 按国家名称 + 年份查询（同一国家同年可能有多个数据源记录） */
    fun findByCountryIgnoreCaseAndYear(country: String, year: Int): List<CountryMacroIndicator>

    /** 按 ISO 3166 alpha-3 国家代码 + 年份查询 */
    fun findByCountryCodeIgnoreCaseAndYear(countryCode: String, year: Int): List<CountryMacroIndicator>

    /** 查询全部数据，按年份降序 */
    fun findAllByOrderByYearDesc(): List<CountryMacroIndicator>
}