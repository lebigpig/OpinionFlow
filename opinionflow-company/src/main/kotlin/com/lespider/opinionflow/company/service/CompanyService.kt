package com.lespider.opinionflow.company.service

import com.lespider.opinionflow.company.domain.BalanceSheet
import com.lespider.opinionflow.company.domain.CashFlowStatement
import com.lespider.opinionflow.company.domain.Company
import com.lespider.opinionflow.company.domain.FinancialIndicatorValue
import com.lespider.opinionflow.company.domain.FinancialReport
import com.lespider.opinionflow.company.domain.IncomeStatement
import com.lespider.opinionflow.company.repo.BalanceSheetRepository
import com.lespider.opinionflow.company.repo.CashFlowStatementRepository
import com.lespider.opinionflow.company.repo.CompanyRepository
import com.lespider.opinionflow.company.repo.FinancialIndicatorValueRepository
import com.lespider.opinionflow.company.repo.FinancialReportRepository
import com.lespider.opinionflow.company.repo.IncomeStatementRepository
import jakarta.persistence.Tuple
import java.math.BigDecimal
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 中国企业财报服务
 * 数据源：company_china 库（company / financial_report / 三表 / 财务指标）
 */
@Service
class CompanyService(
    private val companyRepository: CompanyRepository,
    private val reportRepository: FinancialReportRepository,
    private val incomeRepository: IncomeStatementRepository,
    private val balanceRepository: BalanceSheetRepository,
    private val cashFlowRepository: CashFlowStatementRepository,
    private val indicatorRepository: FinancialIndicatorValueRepository,
) {

    /** 公司列表（关键字模糊匹配股票代码 / 公司名称，分页） */
    @Transactional(readOnly = true)
    fun listCompanies(keyword: String?, page: Int, size: Int): Map<String, Any?> {
        val p = page.coerceAtLeast(0)
        val s = size.coerceIn(1, 500)
        val kw = keyword?.trim().orEmpty()
        val pageable = PageRequest.of(p, s)
        val result = if (kw.isEmpty()) {
            companyRepository.findAll(pageable)
        } else {
            companyRepository.findByCompanyCodeContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(kw, kw, pageable)
        }
        return linkedMapOf(
            "total" to result.totalElements,
            "page" to p,
            "size" to s,
            "list" to result.content.map { companyToMap(it) },
        )
    }

    /** 公司详情 */
    @Transactional(readOnly = true)
    fun getCompany(id: Long): Map<String, Any?>? =
        companyRepository.findById(id).orElse(null)?.let { companyToMap(it) }

    /** 某公司的财报列表（可按财年过滤） */
    @Transactional(readOnly = true)
    fun listReports(companyId: Long, fiscalYear: Int?): List<Map<String, Any?>> {
        val rows = if (fiscalYear != null) {
            reportRepository.findByCompanyIdAndFiscalYearOrderByFiscalPeriodAsc(companyId, fiscalYear)
        } else {
            reportRepository.findByCompanyIdOrderByFiscalYearDescFiscalPeriodDescIdDesc(companyId)
        }
        return rows.map { reportToMap(it) }
    }

    /** 利润表明细 */
    @Transactional(readOnly = true)
    fun listIncome(reportId: Long): List<Map<String, Any?>> =
        incomeRepository.findByReportIdOrderBySortOrderAscIdAsc(reportId).map { statementToMap(it, null) }

    /** 资产负债表明细 */
    @Transactional(readOnly = true)
    fun listBalance(reportId: Long): List<Map<String, Any?>> =
        balanceRepository.findByReportIdOrderBySortOrderAscIdAsc(reportId).map { statementToMap(it, null) }

    /** 现金流量表明细（含 activityType） */
    @Transactional(readOnly = true)
    fun listCashFlow(reportId: Long): List<Map<String, Any?>> =
        cashFlowRepository.findByReportIdOrderBySortOrderAscIdAsc(reportId)
            .map { statementToMap(it, it.activityType) }

    /** 财务指标 */
    @Transactional(readOnly = true)
    fun listIndicators(reportId: Long): List<Map<String, Any?>> =
        indicatorRepository.findByReportIdOrderByIndicatorCodeAsc(reportId).map { indicatorToMap(it) }

    // ========== 私有映射 ==========

    // ===== 连接查询（JOIN / UNION ALL）=====

    /** 公司详情：company LEFT JOIN financial_report（连接查询 1），返回 { company, reports[] } */
    @Transactional(readOnly = true)
    fun getCompanyDetail(companyId: Long): Map<String, Any?>? {
        val rows = reportRepository.findCompanyDetailRows(companyId)
        if (rows.isEmpty()) return null

        val head = rows.first()
        val company = linkedMapOf<String, Any?>(
            "id" to num(head, "company_id")?.toLong(),
            "companyCode" to str(head, "company_code"),
            "companyName" to str(head, "company_name"),
            "shortName" to str(head, "short_name"),
            "exchange" to str(head, "exchange"),
            "industry" to str(head, "industry"),
            "fiscalYearEnd" to str(head, "fiscal_year_end"),
        )

        val reports = rows.mapNotNull { row ->
            val reportId = num(row, "report_id")?.toLong() ?: return@mapNotNull null
            linkedMapOf<String, Any?>(
                "id" to reportId,
                "companyId" to num(row, "company_id")?.toLong(),
                "reportType" to str(row, "report_type"),
                "fiscalYear" to num(row, "fiscal_year")?.toInt(),
                "fiscalPeriod" to str(row, "fiscal_period"),
                "reportDate" to str(row, "report_date"),
                "publishDate" to str(row, "publish_date"),
                "currency" to str(row, "currency"),
                "unit" to str(row, "unit"),
                "auditStatus" to str(row, "audit_status"),
                "parseStatus" to str(row, "parse_status"),
                "incomeCount" to (num(row, "income_count")?.toLong() ?: 0L),
                "balanceCount" to (num(row, "balance_count")?.toLong() ?: 0L),
                "cashflowCount" to (num(row, "cashflow_count")?.toLong() ?: 0L),
                "indicatorCount" to (num(row, "indicator_count")?.toLong() ?: 0L),
            )
        }

        return linkedMapOf("company" to company, "reports" to reports)
    }

    /** 某份财报的各表明细：三表 UNION ALL + 财务指标（连接查询 2） */
    @Transactional(readOnly = true)
    fun getReportStatements(reportId: Long): Map<String, Any?> {
        val grouped = reportRepository.findStatementUnionRows(reportId)
            .groupBy { str(it, "table_type") ?: "" }
        fun rowsOf(type: String): List<Map<String, Any?>> =
            (grouped[type] ?: emptyList()).map { statementRowToMap(it) }
        return linkedMapOf(
            "reportId" to reportId,
            "income" to rowsOf("income"),
            "balance" to rowsOf("balance"),
            "cashflow" to rowsOf("cashflow"),
            "indicators" to rowsOf("indicator"),
        )
    }

    /** 某指标的历史走势：该指标在所有财报（各季度/年度）中的本期值、上期值、同比（财年/期间升序，即时间从左往右） */
    @Transactional(readOnly = true)
    fun getIndicatorHistory(companyId: Long, indicatorCode: String): Map<String, Any?> {
        val rows = reportRepository.findIndicatorHistoryRows(companyId, indicatorCode)
        val points = rows.map { row ->
            linkedMapOf(
                "fiscalYear" to num(row, "fiscal_year")?.toInt(),
                "fiscalPeriod" to str(row, "fiscal_period"),
                "reportType" to str(row, "report_type"),
                "indicatorCode" to str(row, "indicator_code"),
                "indicatorName" to str(row, "indicator_name"),
                "indicatorValue" to plainAny(row, "indicator_value"),
                "valuePrevious" to plainAny(row, "value_previous"),
                "yoyChange" to plainAny(row, "yoy_change"),
            )
        }
        val head = points.firstOrNull()
        if (head == null) {
            return linkedMapOf(
                "companyId" to companyId,
                "indicatorCode" to indicatorCode,
                "indicatorName" to null,
                "unit" to null,
                "points" to emptyList<Map<String, Any?>>(),
            )
        }
        return linkedMapOf(
            "companyId" to companyId,
            "indicatorCode" to indicatorCode,
            "indicatorName" to head["indicatorName"],
            "unit" to null,
            "points" to points,
        )
    }

    /** 某一科目（利润表/资产负债表/现金流量表之一）的历史走势：该科目在所有财报中的本期值、上期值（财年/期间升序） */
    @Transactional(readOnly = true)
    fun getStatementHistory(companyId: Long, tableType: String, itemName: String): Map<String, Any?> {
        val rows: List<Tuple> = if (tableType == "income") {
            reportRepository.findIncomeHistoryRows(companyId, itemName)
        } else if (tableType == "balance") {
            reportRepository.findBalanceHistoryRows(companyId, itemName)
        } else if (tableType == "cashflow") {
            reportRepository.findCashFlowHistoryRows(companyId, itemName)
        } else {
            emptyList()
        }
        val points = rows.map { row ->
            linkedMapOf(
                "fiscalYear" to num(row, "fiscal_year")?.toInt(),
                "fiscalPeriod" to str(row, "fiscal_period"),
                "reportType" to str(row, "report_type"),
                "itemName" to str(row, "item_name"),
                "unit" to str(row, "unit"),
                "valueCurrent" to plainAny(row, "value_current"),
                "valuePrevious" to plainAny(row, "value_previous"),
            )
        }
        return linkedMapOf(
            "companyId" to companyId,
            "tableType" to tableType,
            "itemName" to itemName,
            "points" to points,
        )
    }

    private fun companyToMap(c: Company): Map<String, Any?> = linkedMapOf(
        "id" to (c.id ?: 0L),
        "companyCode" to c.companyCode,
        "companyName" to c.companyName,
        "shortName" to c.shortName,
        "exchange" to c.exchange,
        "industry" to c.industry,
        "fiscalYearEnd" to c.fiscalYearEnd,
        "createdAt" to c.createdAt?.toString(),
    )

    private fun reportToMap(r: FinancialReport): Map<String, Any?> = linkedMapOf(
        "id" to (r.id ?: 0L),
        "companyId" to r.companyId,
        "reportType" to r.reportType,
        "fiscalYear" to r.fiscalYear,
        "fiscalPeriod" to r.fiscalPeriod,
        "reportDate" to r.reportDate?.toString(),
        "publishDate" to r.publishDate?.toString(),
        "currency" to r.currency,
        "unit" to r.unit,
        "auditStatus" to r.auditStatus,
        "pdfFilePath" to r.pdfFilePath,
        "pdfFileHash" to r.pdfFileHash,
        "parseStatus" to r.parseStatus,
        "parsedAt" to r.parsedAt?.toString(),
        "createdAt" to r.createdAt?.toString(),
    )

    /** 利润表 → Map */
    private fun statementToMap(
        i: IncomeStatement,
        activityType: String?,
    ): Map<String, Any?> = statementToMap(
        id = i.id, reportId = i.reportId, itemName = i.itemName, itemLevel = i.itemLevel,
        parentItem = i.parentItem, isTotal = i.isTotal, isSubItem = i.isSubItem, unit = i.unit,
        valueCurrent = i.valueCurrent, valuePrevious = i.valuePrevious, noteRef = i.noteRef,
        sortOrder = i.sortOrder, activityType = activityType,
    )

    /** 资产负债表 → Map */
    private fun statementToMap(
        b: BalanceSheet,
        activityType: String?,
    ): Map<String, Any?> = statementToMap(
        id = b.id, reportId = b.reportId, itemName = b.itemName, itemLevel = b.itemLevel,
        parentItem = b.parentItem, isTotal = b.isTotal, isSubItem = b.isSubItem, unit = b.unit,
        valueCurrent = b.valueCurrent, valuePrevious = b.valuePrevious, noteRef = b.noteRef,
        sortOrder = b.sortOrder, activityType = activityType,
    )

    /** 现金流量表 → Map */
    private fun statementToMap(
        c: CashFlowStatement,
        activityType: String?,
    ): Map<String, Any?> = statementToMap(
        id = c.id, reportId = c.reportId, itemName = c.itemName, itemLevel = c.itemLevel,
        parentItem = c.parentItem, isTotal = c.isTotal, isSubItem = c.isSubItem, unit = c.unit,
        valueCurrent = c.valueCurrent, valuePrevious = c.valuePrevious, noteRef = c.noteRef,
        sortOrder = c.sortOrder, activityType = activityType,
    )

    /** 三表共有字段 → Map */
    private fun statementToMap(
        id: Long?,
        reportId: Long,
        itemName: String?,
        itemLevel: Int?,
        parentItem: String?,
        isTotal: Int?,
        isSubItem: Int?,
        unit: String?,
        valueCurrent: BigDecimal?,
        valuePrevious: BigDecimal?,
        noteRef: String?,
        sortOrder: Int?,
        activityType: String?,
    ): Map<String, Any?> = linkedMapOf(
        "id" to (id ?: 0L),
        "reportId" to reportId,
        "itemName" to itemName,
        "itemLevel" to itemLevel,
        "parentItem" to parentItem,
        "isTotal" to isTotal,
        "isSubItem" to isSubItem,
        "activityType" to activityType,
        "unit" to unit,
        "valueCurrent" to plain(valueCurrent),
        "valuePrevious" to plain(valuePrevious),
        "noteRef" to noteRef,
        "sortOrder" to sortOrder,
    )

    private fun indicatorToMap(v: FinancialIndicatorValue): Map<String, Any?> = linkedMapOf(
        "id" to (v.id ?: 0L),
        "reportId" to v.reportId,
        "indicatorCode" to v.indicatorCode,
        "indicatorName" to v.indicatorName,
        "indicatorValue" to plain(v.indicatorValue),
        "valuePrevious" to plain(v.valuePrevious),
        "yoyChange" to plain(v.yoyChange),
        "createdAt" to v.createdAt?.toString(),
    )

    /** 报表行（UNION 连接查询结果）→ Map */
    private fun statementRowToMap(row: Tuple): Map<String, Any?> = linkedMapOf(
        "tableType" to str(row, "table_type"),
        "id" to num(row, "id")?.toLong(),
        "itemName" to str(row, "item_name"),
        "itemLevel" to num(row, "item_level")?.toInt(),
        "parentItem" to str(row, "parent_item"),
        "isTotal" to num(row, "is_total")?.toInt(),
        "isSubItem" to num(row, "is_sub_item")?.toInt(),
        "activityType" to str(row, "activity_type"),
        "unit" to str(row, "unit"),
        "valueCurrent" to plainAny(row, "value_current"),
        "valuePrevious" to plainAny(row, "value_previous"),
        "noteRef" to str(row, "note_ref"),
        "sortOrder" to num(row, "sort_order")?.toInt(),
        "yoyChange" to plainAny(row, "yoy_change"),
    )

    /** 读取 native 查询 Tuple 的列（别名取不到时返回 null） */
    private fun raw(row: Tuple, alias: String): Any? =
        try { row.get(alias) } catch (e: Exception) { null }

    private fun num(row: Tuple, alias: String): Number? = raw(row, alias) as? Number

    private fun str(row: Tuple, alias: String): String? = raw(row, alias)?.toString()

    private fun plainAny(row: Tuple, alias: String): String? = when (val v = raw(row, alias)) {
        null -> null
        is BigDecimal -> v.toPlainString()
        else -> v.toString()
    }
    /** BigDecimal 转字符串（保留精度，便于前端展示与编辑） */
    private fun plain(v: BigDecimal?): String? = v?.toPlainString()
}