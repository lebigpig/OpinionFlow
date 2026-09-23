package com.lespider.opinionflow.ai.service

import com.fasterxml.jackson.databind.JsonNode
import com.lespider.opinionflow.api.company.CompanyUsFeignClient
import dev.langchain4j.agent.tool.Tool
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 美国企业（美股）财报查询工具 — 供 LangChain4j Agent 调用。
 * 数据来源：opinionflow-company 服务（company_us 库：公司 / 财务报告 / 三表 / 财务指标 / 同行对比）
 * AI 通过 Function Calling 自主决定何时查询、查询哪家公司的哪个指标。
 *
 * 与 [CompanyFinanceTool]（中国库 company_china）一一对应，仅数据源与措辞不同。
 */
@Component
class CompanyUsFinanceTool(
    private val companyUsFeignClient: CompanyUsFeignClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 根据公司关键词或代码查询美股公司基础信息与财报摘要。
     * keyword 支持股票代码（如 AAPL）或公司名称（如 Apple）。
     */
    @Tool("查询美国上市公司（美股）基础信息与财报列表。当你需要了解某家美股公司的基本情况、所属行业板块、最新财报（利润表/资产负债表/现金流量表）及财务指标时，请调用此工具。参数 keyword 为股票代码（如 AAPL）或公司名称。")
    fun queryCompany(keyword: String): String {
        log.info("[Agent Tool] 查询美股公司: keyword='{}'", keyword)
        return try {
            val result = companyUsFeignClient.list(keyword, null, 0, 5, null, null)
            formatCompanyList(result)
        } catch (e: Exception) {
            log.warn("[Agent Tool] 美股公司查询失败: {}", e.message)
            "公司查询失败：${e.message}"
        }
    }

    /**
     * 查询美股公司详情（含各财报与各表行数）。companyId 为 queryCompany 返回的公司 id。
     */
    @Tool("查询美国上市公司详情与财报行数概览。参数 companyId 为 queryCompany 返回的公司 id。返回该公司基础信息、各财年报表（含 SEC 表单类型）、各表行数。")
    fun queryCompanyDetail(companyId: Long): String {
        log.info("[Agent Tool] 查询美股公司详情: companyId={}", companyId)
        return try {
            formatNode("公司详情", companyUsFeignClient.detail(companyId))
        } catch (e: Exception) {
            log.warn("[Agent Tool] 美股公司详情查询失败: {}", e.message)
            "公司详情查询失败：${e.message}"
        }
    }

    /**
     * 查询某美股公司的利润表（按报表 id）。reportId 可从 queryCompany 的返回中获取。
     */
    @Tool("查询美国上市公司利润表明细（US GAAP）。参数 reportId 为财报 id（从 queryCompany / queryCompanyDetail 返回中获取）。返回营收/成本/利润等科目本期值、上期值及 US GAAP 科目编码。")
    fun queryIncomeStatement(reportId: Long): String {
        log.info("[Agent Tool] 查询美股利润表: reportId={}", reportId)
        return try {
            formatNode("利润表明细", companyUsFeignClient.income(reportId))
        } catch (e: Exception) {
            log.warn("[Agent Tool] 美股利润表查询失败: {}", e.message)
            "利润表查询失败：${e.message}"
        }
    }

    /**
     * 查询某美股公司的资产负债表（按报表 id）。
     */
    @Tool("查询美国上市公司资产负债表明细（US GAAP）。参数 reportId 为财报 id。返回资产/负债/权益各科目本期值、上期值。")
    fun queryBalanceSheet(reportId: Long): String {
        log.info("[Agent Tool] 查询美股资产负债表: reportId={}", reportId)
        return try {
            formatNode("资产负债表明细", companyUsFeignClient.balance(reportId))
        } catch (e: Exception) {
            log.warn("[Agent Tool] 美股资产负债表查询失败: {}", e.message)
            "资产负债表查询失败：${e.message}"
        }
    }

    /**
     * 查询某美股公司的现金流量表（按报表 id）。
     */
    @Tool("查询美国上市公司现金流量表明细（US GAAP）。参数 reportId 为财报 id。返回经营/投资/筹资活动现金流量各项本期值、上期值。")
    fun queryCashFlow(reportId: Long): String {
        log.info("[Agent Tool] 查询美股现金流量表: reportId={}", reportId)
        return try {
            formatNode("现金流量表明细", companyUsFeignClient.cashflow(reportId))
        } catch (e: Exception) {
            log.warn("[Agent Tool] 美股现金流量表查询失败: {}", e.message)
            "现金流量表查询失败：${e.message}"
        }
    }

    /**
     * 查询财务指标（如 ROE、毛利率、净利率、EPS 等），按报表 id。
     */
    @Tool("查询美国上市公司的财务指标（如 ROE、毛利率、净利率、EPS、资产负债率等）。参数 reportId 为财报 id。返回指标名称、本期值、上期值、同比变化。")
    fun queryFinancialIndicators(reportId: Long): String {
        log.info("[Agent Tool] 查询美股财务指标: reportId={}", reportId)
        return try {
            formatNode("财务指标", companyUsFeignClient.indicators(reportId))
        } catch (e: Exception) {
            log.warn("[Agent Tool] 美股财务指标查询失败: {}", e.message)
            "财务指标查询失败：${e.message}"
        }
    }

    /**
     * 查询某指标在历史各财年/季度的走势。indicatorCode 从 queryFinancialIndicators 返回中获取。
     */
    @Tool("查询美国上市公司某财务指标的历史走势。参数 companyId 为公司 id，indicatorCode 为指标代码（从 queryFinancialIndicators 返回中获取）。返回该指标各季度/年度本期值、上期值、同比。")
    fun queryIndicatorHistory(companyId: Long, indicatorCode: String): String {
        log.info("[Agent Tool] 查询美股指标走势: companyId={}, indicator={}", companyId, indicatorCode)
        return try {
            formatNode("指标($indicatorCode)历史走势", companyUsFeignClient.indicatorHistory(companyId, indicatorCode))
        } catch (e: Exception) {
            log.warn("[Agent Tool] 美股指标走势查询失败: {}", e.message)
            "指标走势查询失败：${e.message}"
        }
    }

    /**
     * 查询同行业同一指标的公司横向对比（美股）。
     */
    @Tool("查询美股同行业公司某财务指标的横向对比。参数 indicatorCode 为指标代码，industry 为细分行业（如 Consumer Electronics），fiscalYear 为财年（如 2024），fiscalPeriod 为期间（Q1/Q2/Q3/Q4/FY）。返回同行业公司该指标值降序排列。")
    fun queryPeerCompare(indicatorCode: String, industry: String?, fiscalYear: Int, fiscalPeriod: String): String {
        log.info("[Agent Tool] 查询美股同行对比: indicator={}, industry={}, year={}, period={}", indicatorCode, industry, fiscalYear, fiscalPeriod)
        return try {
            formatNode(
                "同行对比($indicatorCode, $fiscalYear $fiscalPeriod)",
                companyUsFeignClient.peerCompare(indicatorCode, industry, fiscalYear, fiscalPeriod),
            )
        } catch (e: Exception) {
            log.warn("[Agent Tool] 美股同行对比查询失败: {}", e.message)
            "同行对比查询失败：${e.message}"
        }
    }

    private fun formatCompanyList(node: JsonNode): String {
        val total = node.get("total")?.asLong() ?: 0
        if (total == 0L) {
            return "未找到匹配的美股公司，请尝试不同的关键词（支持股票代码如 AAPL 或公司名称）。"
        }
        val sb = StringBuilder()
        sb.appendLine("共找到 $total 家公司（显示前 5 家）：")
        // CompanyUsService.listCompanies 返回 { total, page, size, sortBy, sortDir, list: [...] }
        val list = node.get("list") ?: node.get("content")
        if (list != null && list.isArray) {
            for (item in list) {
                sb.appendLine(
                    "- id=${item.get("id")?.asLong()} | 代码=${item.get("companyCode")?.asText()} " +
                        "| 名称=${item.get("companyName")?.asText()} | 简称=${item.get("shortName")?.asText()} " +
                        "| 交易所=${item.get("exchange")?.asText()} | 板块=${item.get("sector")?.asText()} " +
                        "| 细分行业=${item.get("industry")?.asText()}",
                )
            }
        } else {
            sb.appendLine(formatNode("", node))
        }
        return sb.toString().trim()
    }

    private fun formatNode(title: String, node: JsonNode): String {
        if (node == null || node.isMissingNode) return "$title：(无数据)"
        if (node.isArray && node.size() == 0) return "$title：(无数据)"
        if (node.isArray) {
            val sb = StringBuilder(title).appendLine("：")
            val maxRows = 30
            var count = 0
            for (item in node) {
                if (count >= maxRows) {
                    sb.appendLine("...（共 ${node.size()} 行，仅显示前 $maxRows 行）")
                    break
                }
                sb.appendLine("  ${flattenJson(item)}")
                count++
            }
            return sb.toString().trim()
        }
        return "$title：${flattenJson(node)}"
    }

    /** 将 JSON 节点转为一行的 key=value 形式，便于 AI 阅读 */
    private fun flattenJson(node: JsonNode): String {
        if (node == null || node.isMissingNode) return ""
        if (node.isValueNode) return node.asText()
        val sb = StringBuilder()
        val fields = node.fields()
        while (fields.hasNext()) {
            val (k, v) = fields.next()
            if (v == null || v.isMissingNode || v.isNull) continue
            sb.append(k).append("=")
            when {
                v.isValueNode -> sb.append(v.asText())
                v.isArray -> sb.append("[${v.joinToString(",") { flattenJson(it) }}]")
                v.isObject -> sb.append("{${flattenJson(v)}}")
                else -> sb.append(v.asText())
            }
            sb.append(" | ")
        }
        return sb.toString().trimEnd(' ', '|').trim()
    }
}
