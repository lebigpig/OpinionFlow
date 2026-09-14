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

    /** 公司列表（关键字模糊匹配股票代码/公司名称，可按行业筛选、按列升降序排序，分页） */
    @GetMapping("/list")
    fun list(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) industry: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) sortBy: String?,
        @RequestParam(required = false) sortDir: String?,
    ): Map<String, Any?> = companyService.listCompanies(keyword, industry, page, size, sortBy, sortDir)

    /** 全部所属行业（去重、升序，供前端下拉框选择） */
    @GetMapping("/industries")
    fun industries(): List<String> = companyService.listIndustries()

    /** 公司详情（连接查询 1）：company LEFT JOIN financial_report，含各财报与各表行数 */
    @GetMapping("/{id:\\d+}/detail")
    fun detail(@PathVariable id: Long): Map<String, Any?> =
        companyService.getCompanyDetail(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "公司不存在: id=$id")

    /** 各表明细（连接查询 2）：利润表 UNION ALL 资产负债表 UNION ALL 现金流量表 UNION ALL 财务指标 */
    @GetMapping("/reports/{reportId:\\d+}/detail")
    fun reportDetail(@PathVariable reportId: Long): Map<String, Any?> =
        companyService.getReportStatements(reportId)

    /** 公司基础信息 */
    @GetMapping("/{id:\\d+}")
    fun getCompany(@PathVariable id: Long): Map<String, Any?> =
        companyService.getCompany(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "公司不存在: id=$id")

    /** 某公司的财报列表（可按财年过滤） */
    @GetMapping("/{id:\\d+}/reports")
    fun reports(
        @PathVariable id: Long,
        @RequestParam(required = false) fiscalYear: Int?,
    ): List<Map<String, Any?>> = companyService.listReports(id, fiscalYear)

    /** 利润表明细 */
    @GetMapping("/reports/{reportId:\\d+}/income")
    fun income(@PathVariable reportId: Long): List<Map<String, Any?>> =
        companyService.listIncome(reportId)

    /** 资产负债表明细 */
    @GetMapping("/reports/{reportId:\\d+}/balance")
    fun balance(@PathVariable reportId: Long): List<Map<String, Any?>> =
        companyService.listBalance(reportId)

    /** 现金流量表明细 */
    @GetMapping("/reports/{reportId:\\d+}/cashflow")
    fun cashflow(@PathVariable reportId: Long): List<Map<String, Any?>> =
        companyService.listCashFlow(reportId)

    /** 财务指标 */
    @GetMapping("/reports/{reportId:\\d+}/indicators")
    fun indicators(@PathVariable reportId: Long): List<Map<String, Any?>> =
        companyService.listIndicators(reportId)

    /** 某指标的历史走势：该指标在所有财报（各季度/年度）中的本期值、上期值、同比（时间升序） */
    @GetMapping("/{companyId:\\d+}/indicators/{indicatorCode}/history")
    fun indicatorHistory(
        @PathVariable companyId: Long,
        @PathVariable indicatorCode: String,
    ): Map<String, Any?> = companyService.getIndicatorHistory(companyId, indicatorCode)

    /**
     * 同行业公司横向对比：点击走势图某季度柱子后调用
     * 取「同一指标 + 同一财年/季度」下同行业所有公司的指标值（按值降序）
     */
    @GetMapping("/indicators/{indicatorCode}/peer-compare")
    fun peerCompare(
        @PathVariable indicatorCode: String,
        @RequestParam(required = false) industry: String?,
        @RequestParam fiscalYear: Int,
        @RequestParam fiscalPeriod: String,
    ): Map<String, Any?> = companyService.getPeerIndicatorCompare(indicatorCode, industry, fiscalYear, fiscalPeriod)

    /**
     * 同行业「某一科目」横向对比（利润表 / 资产负债表 / 现金流量表）：
     * 点击三张报表走势图某季度柱子后调用
     */
    @GetMapping("/statements/{tableType}/peer-compare")
    fun statementPeerCompare(
        @PathVariable tableType: String,
        @RequestParam itemName: String,
        @RequestParam(required = false) industry: String?,
        @RequestParam fiscalYear: Int,
        @RequestParam fiscalPeriod: String,
    ): Map<String, Any?> =
        companyService.getPeerStatementCompare(tableType, itemName, industry, fiscalYear, fiscalPeriod)

    /** 某一科目（利润表/资产负债表/现金流量表）的历史走势：该科目在所有财报中的本期值、上期值（时间升序） */
    @GetMapping("/{companyId:\\d+}/statements/{tableType}/history")
    fun statementHistory(
        @PathVariable companyId: Long,
        @PathVariable tableType: String,
        @RequestParam itemName: String,
    ): Map<String, Any?> = companyService.getStatementHistory(companyId, tableType, itemName)
}
