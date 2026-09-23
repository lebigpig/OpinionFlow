package com.lespider.opinionflow.api.company

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

/**
 * 美国企业（美股）财报服务 Feign 客户端
 * AI 服务（美国企业专家 Agent）通过此接口调用 opinionflow-company 服务的 company_us 数据源
 * 对应服务：opinionflow-company（端口 9206，base /api/company/us）
 * 数据源：company_us 库（SEC EDGAR / US GAAP）
 *
 * 注意：与 [CompanyFeignClient] 同名服务不同 base path，必须用独立 contextId，
 *      否则 Feign 配置类名冲突（<name>.FeignClientSpecification 重复注册）。
 */
@FeignClient(name = "opinionflow-company", contextId = "companyUsFeignClient", path = "/api/company/us")
interface CompanyUsFeignClient {

    /** 公司列表（关键字/行业/排序/分页） */
    @GetMapping("/list")
    fun list(
        @RequestParam("keyword") keyword: String?,
        @RequestParam("industry") industry: String?,
        @RequestParam("page") page: Int,
        @RequestParam("size") size: Int,
        @RequestParam("sortBy") sortBy: String?,
        @RequestParam("sortDir") sortDir: String?,
    ): JsonNode

    /** 全部所属行业 */
    @GetMapping("/industries")
    fun industries(): JsonNode

    /** 公司详情（含各财报与各表行数） */
    @GetMapping("/{id}/detail")
    fun detail(@PathVariable("id") id: Long): JsonNode

    /** 公司基础信息 */
    @GetMapping("/{id}")
    fun getCompany(@PathVariable("id") id: Long): JsonNode

    /** 某公司的财报列表（可按财年过滤） */
    @GetMapping("/{id}/reports")
    fun reports(
        @PathVariable("id") id: Long,
        @RequestParam("fiscalYear") fiscalYear: Int?,
    ): JsonNode

    /** 利润表明细 */
    @GetMapping("/reports/{reportId}/income")
    fun income(@PathVariable("reportId") reportId: Long): JsonNode

    /** 资产负债表明细 */
    @GetMapping("/reports/{reportId}/balance")
    fun balance(@PathVariable("reportId") reportId: Long): JsonNode

    /** 现金流量表明细 */
    @GetMapping("/reports/{reportId}/cashflow")
    fun cashflow(@PathVariable("reportId") reportId: Long): JsonNode

    /** 财务指标 */
    @GetMapping("/reports/{reportId}/indicators")
    fun indicators(@PathVariable("reportId") reportId: Long): JsonNode

    /** 某指标的历史走势 */
    @GetMapping("/{companyId}/indicators/{indicatorCode}/history")
    fun indicatorHistory(
        @PathVariable("companyId") companyId: Long,
        @PathVariable("indicatorCode") indicatorCode: String,
    ): JsonNode

    /** 某科目（利润表/资产负债表/现金流量表）的历史走势 */
    @GetMapping("/{companyId}/statements/{tableType}/history")
    fun statementHistory(
        @PathVariable("companyId") companyId: Long,
        @PathVariable("tableType") tableType: String,
        @RequestParam("itemName") itemName: String,
    ): JsonNode

    /** 同行业「某一指标」横向对比 */
    @GetMapping("/indicators/{indicatorCode}/peer-compare")
    fun peerCompare(
        @PathVariable("indicatorCode") indicatorCode: String,
        @RequestParam("industry") industry: String?,
        @RequestParam("fiscalYear") fiscalYear: Int,
        @RequestParam("fiscalPeriod") fiscalPeriod: String,
    ): JsonNode

    /** 同行业「某一科目」横向对比（利润表/资产负债表/现金流量表） */
    @GetMapping("/statements/{tableType}/peer-compare")
    fun statementPeerCompare(
        @PathVariable("tableType") tableType: String,
        @RequestParam("itemName") itemName: String,
        @RequestParam("industry") industry: String?,
        @RequestParam("fiscalYear") fiscalYear: Int,
        @RequestParam("fiscalPeriod") fiscalPeriod: String,
    ): JsonNode
}
