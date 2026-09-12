package com.lespider.opinionflow.echart.controller

import com.lespider.opinionflow.echart.service.CountryMacroIndicatorService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * 国家宏观指标 REST API
 * 世界格局地图：点击国家后弹出表单，按 country / countryCode 查询 spider 库 country_macro_indicators 表
 * base: /api/country-macro-indicators
 */
@RestController
@RequestMapping("/api/country-macro-indicators")
class CountryMacroIndicatorController(
    private val service: CountryMacroIndicatorService,
) {

    /**
     * 按国家查询最近一年的宏观指标（世界格局地图点击国家弹窗推荐）。
     * 示例：GET /api/country-macro-indicators/latest?country=China
     */
    @GetMapping("/latest")
    fun latest(@RequestParam country: String): Map<String, Any?> {
        return service.findLatest(country)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "未查询到 [$country] 的宏观指标数据")
    }

    /**
     * 按国家查询全部年度宏观指标（按年份降序）。
     * 示例：GET /api/country-macro-indicators/by-country?country=China
     */
    @GetMapping("/by-country")
    fun byCountry(@RequestParam country: String): List<Map<String, Any?>> {
        return service.findByCountry(country)
    }

    /**
     * 按国家 + 年份查询宏观指标（同一国家同年可能有多个数据源记录）。
     * 示例：GET /api/country-macro-indicators/by-year?country=China&year=2024
     */
    @GetMapping("/by-year")
    fun byYear(
        @RequestParam country: String,
        @RequestParam year: Int,
    ): List<Map<String, Any?>> {
        return service.findByCountryAndYear(country, year)
    }

    /**
     * 按 id 更新宏观指标记录（表单编辑提交）。
     * body 中出现的字段才更新，未出现的字段保持原值；null / 空串视为清空。
     */
    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val updated = try {
            service.update(id, body)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }
        return updated ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "宏观指标记录不存在: id=$id")
    }

    /** 有数据的国家清单（供前端弹窗联想/校验国家名） */
    @GetMapping("/countries")
    fun countries(): List<Map<String, Any?>> = service.listCountries()

    /** 查询所有国家宏观指标（全部年份，供调试/管理） */
    @GetMapping
    fun list(): List<Map<String, Any?>> = service.listAll()
}