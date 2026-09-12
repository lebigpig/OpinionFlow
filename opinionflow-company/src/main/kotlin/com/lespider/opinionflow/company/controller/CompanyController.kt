package com.lespider.opinionflow.company.controller

import com.lespider.opinionflow.company.service.CompanyService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * 中国企业财报 REST API
 * 数据源：company_china 库
 * base: /api/company
 */
@RestController
@RequestMapping("/api/company")
class CompanyController(
    private val companyService: CompanyService,
) {

    /** 公司列表（company 主表：关键字模糊匹配股票代码 / 公司名称，分页） */
    @GetMapping("/list")
    fun list(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Map<String, Any?> = companyService.listCompanies(keyword, page, size)

    /** 公司详情（连接查询 1）：company LEFT JOIN financial_report，含各财报与各表行数 */
    @GetMapping("/{id}/detail")
    fun detail(@PathVariable id: Long): Map<String, Any?> =
        companyService.getCompanyDetail(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "公司不存在: id=$id")

    /** 各表明细（连接查询 2）：利润表 UNION ALL 资产负债表 UNION ALL 现金流量表 UNION ALL 财务指标 */
    @GetMapping("/reports/{reportId}/detail")
    fun reportDetail(@PathVariable reportId: Long): Map<String, Any?> =
        companyService.getReportStatements(reportId)

    /** 公司基础信息 */
    @GetMapping("/{id}")
    fun getCompany(@PathVariable id: Long): Map<String, Any?> =
        companyService.getCompany(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "公司不存在: id=$id")

    /** 某公司的财报列表（可按财年过滤） */
    @GetMapping("/{id}/reports")
    fun reports(
        @PathVariable id: Long,
        @RequestParam(required = false) fiscalYear: Int?,
    ): List<Map<String, Any?>> = companyService.listReports(id, fiscalYear)

    /** 利润表明细 */
    @GetMapping("/reports/{reportId}/income")
    fun income(@PathVariable reportId: Long): List<Map<String, Any?>> =
        companyService.listIncome(reportId)

    /** 资产负债表明细 */
    @GetMapping("/reports/{reportId}/balance")
    fun balance(@PathVariable reportId: Long): List<Map<String, Any?>> =
        companyService.listBalance(reportId)

    /** 现金流量表明细 */
    @GetMapping("/reports/{reportId}/cashflow")
    fun cashflow(@PathVariable reportId: Long): List<Map<String, Any?>> =
        companyService.listCashFlow(reportId)

    /** 财务指标 */
    @GetMapping("/reports/{reportId}/indicators")
    fun indicators(@PathVariable reportId: Long): List<Map<String, Any?>> =
        companyService.listIndicators(reportId)
}
