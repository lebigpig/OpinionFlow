package com.lespider.opinionflow.company.us.service

import com.lespider.opinionflow.company.us.domain.UsBalanceSheet
import com.lespider.opinionflow.company.us.domain.UsCashFlowStatement
import com.lespider.opinionflow.company.us.domain.UsCompany
import com.lespider.opinionflow.company.us.domain.UsFinancialIndicator
import com.lespider.opinionflow.company.us.domain.UsFinancialReport
import com.lespider.opinionflow.company.us.domain.UsIncomeStatement
import com.lespider.opinionflow.company.us.repo.UsBalanceSheetRepository
import com.lespider.opinionflow.company.us.repo.UsCashFlowStatementRepository
import com.lespider.opinionflow.company.us.repo.UsCompanyRepository
import com.lespider.opinionflow.company.us.repo.UsFinancialIndicatorRepository
import com.lespider.opinionflow.company.us.repo.UsFinancialReportRepository
import com.lespider.opinionflow.company.us.repo.UsIncomeStatementRepository
import jakarta.persistence.Tuple
import jakarta.persistence.criteria.Predicate
import java.math.BigDecimal
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 美国企业（美股）财报服务
 * 数据源：company_us 库（company / financial_report / 三表 / 财务指标）
 *
 * 每个方法都显式指定 transactionManager = "usTransactionManager"（第二数据源）；
 * 返回的 JSON 结构与 CompanyService（中国库）完全一致（ticker → companyCode），
 * 使前端「美国企业」页面与「中国企业」页面共用同一套渲染逻辑。
 */
@Service
class CompanyUsService(
    private val companyRepository: UsCompanyRepository,
    private val reportRepository: UsFinancialReportRepository,
    private val incomeRepository: UsIncomeStatementRepository,
    private val balanceRepository: UsBalanceSheetRepository,
    private val cashFlowRepository: UsCashFlowStatementRepository,
    private val indicatorRepository: UsFinancialIndicatorRepository,
) {

    /** 公司列表：关键字模糊匹配（股票代码/公司名称）+ 行业筛选 + 排序，分页 */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
    fun listCompanies(
        keyword: String?,
        industry: String?,
        page: Int,
        size: Int,
        sortBy: String?,
        sortDir: String?,
    ): Map<String, Any?> {
        val p = page.coerceAtLeast(0)
        val s = size.coerceIn(1, 500)
        val kw = keyword?.trim().orEmpty()
        val ind = industry?.trim().orEmpty()

        // 白名单：只允许对前端表格列排序（companyCode 对应美股库的 ticker 列）
        val field = when (sortBy) {
            "companyCode" -> "ticker"
            "companyName", "shortName", "exchange", "industry" -> sortBy
            else -> "ticker"
        }
        val desc = sortDir?.lowercase() == "desc"
        val dir = if (desc) Sort.Direction.DESC else Sort.Direction.ASC
        val pageable = PageRequest.of(p, s, Sort.by(dir, field))

        // 动态条件：关键字（股票代码/公司名称，忽略大小写） + 行业精确匹配
        val spec = Specification<UsCompany> { root, _, cb ->
            val preds = mutableListOf<Predicate>()
            if (kw.isNotEmpty()) {
                val like = "%${kw.lowercase()}%"
                preds.add(
                    cb.or(
                        cb.like(cb.lower(root.get<String>("ticker")), like),
                        cb.like(cb.lower(root.get<String>("companyName")), like),
                    ),
                )
            }
            if (ind.isNotEmpty()) {
                preds.add(cb.equal(root.get<String>("industry"), ind))
            }
            cb.and(*preds.toTypedArray())
        }
        val result = companyRepository.findAll(spec, pageable)
        return linkedMapOf(
            "total" to result.totalElements,
            "page" to p,
            "size" to s,
            "sortBy" to if (field == "ticker") "companyCode" else field,
            "sortDir" to if (desc) "desc" else "asc",
            "list" to result.content.map { companyToMap(it) },
        )
    }

    /** 全部所属行业（去重、升序，供前端下拉框选择） */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
    fun listIndustries(): List<String> = companyRepository.findDistinctIndustries()

    /** 公司基础信息 */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
    fun getCompany(id: Long): Map<String, Any?>? =
        companyRepository.findById(id).orElse(null)?.let { companyToMap(it) }

    /** 某公司的财报列表（可按财年过滤） */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
    fun listReports(companyId: Long, fiscalYear: Int?): List<Map<String, Any?>> {
        val rows = if (fiscalYear != null) {
            reportRepository.findByCompanyIdAndFiscalYearOrderByFiscalPeriodAsc(companyId, fiscalYear)
        } else {
            reportRepository.findByCompanyIdOrderByFiscalYearDescFiscalPeriodDescIdDesc(companyId)
        }
        return rows.map { reportToMap(it) }
    }

    /** 利润表明细 */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
    fun listIncome(reportId: Long): List<Map<String, Any?>> =
        incomeRepository.findByReportIdOrderBySortOrderAscIdAsc(reportId).map { statementToMap(it, null) }

    /** 资产负债表明细 */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
    fun listBalance(reportId: Long): List<Map<String, Any?>> =
        balanceRepository.findByReportIdOrderBySortOrderAscIdAsc(reportId).map { statementToMap(it, null) }

    /** 现金流量表明细（含 activityType） */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
    fun listCashFlow(reportId: Long): List<Map<String, Any?>> =
        cashFlowRepository.findByReportIdOrderBySortOrderAscIdAsc(reportId)
            .map { statementToMap(it, it.activityType) }

    /** 财务指标 */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
    fun listIndicators(reportId: Long): List<Map<String, Any?>> =
        indicatorRepository.findByReportIdOrderByIndicatorCodeAsc(reportId).map { indicatorToMap(it) }

    // ===== 连接查询（JOIN / UNION ALL）=====

    /** 公司详情：company LEFT JOIN financial_report（连接查询 1），返回 { company, reports[] } */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
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
            "sector" to str(head, "sector"),
            "industry" to str(head, "industry"),
            "cik" to str(head, "cik"),
            "isin" to str(head, "isin"),
            "fiscalYearEnd" to str(head, "fiscal_year_end"),
            "country" to str(head, "country"),
            "currency" to str(head, "company_currency"),
        )

        val reports = rows.mapNotNull { row ->
            val reportId = num(row, "report_id")?.toLong() ?: return@mapNotNull null
            linkedMapOf<String, Any?>(
                "id" to reportId,
                "companyId" to num(row, "company_id")?.toLong(),
                "reportType" to str(row, "report_type"),
                "fiscalYear" to num(row, "fiscal_year")?.toInt(),
                "fiscalPeriod" to str(row, "fiscal_period"),
                "formType" to str(row, "form_type"),
                "reportDate" to str(row, "report_date"),
                "publishDate" to str(row, "publish_date"),
                "accessionNo" to str(row, "accession_no"),
                "filingUrl" to str(row, "filing_url"),
                "currency" to str(row, "currency"),
                "unit" to str(row, "unit"),
                "scaleFactor" to num(row, "scale_factor")?.toInt(),
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
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
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

    /** 某指标的历史走势：该指标在所有财报（各季度/年度）中的本期值、上期值、同比（财年/期间升序） */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
    fun getIndicatorHistory(companyId: Long, indicatorCode: String): Map<String, Any?> {
        val rows = reportRepository.findIndicatorHistoryRows(companyId, indicatorCode)
        val points = rows.map { row ->
            linkedMapOf(
                "fiscalYear" to num(row, "fiscal_year")?.toInt(),
                "fiscalPeriod" to str(row, "fiscal_period"),
                "reportType" to str(row, "report_type"),
                "indicatorCode" to str(row, "indicator_code"),
                "indicatorName" to str(row, "indicator_name"),
                "indicatorNameEn" to str(row, "indicator_name_en"),
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

    /**
     * 同行业公司横向对比：同一指标 + 同一期间（财年/季度）下，同行业所有公司的指标值
     * industry 为空表示不限定行业（比较全部公司）
     */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
    fun getPeerIndicatorCompare(
        indicatorCode: String,
        industry: String?,
        fiscalYear: Int,
        fiscalPeriod: String,
    ): Map<String, Any?> {
        val ind = industry?.trim().orEmpty()
        val rows = reportRepository.findPeerIndicatorRows(indicatorCode, fiscalYear, fiscalPeriod, ind)
        val list = rows.map { row ->
            linkedMapOf(
                "companyId" to num(row, "company_id")?.toLong(),
                "companyCode" to str(row, "company_code"),
                "companyName" to str(row, "company_name"),
                "shortName" to str(row, "short_name"),
                "industry" to str(row, "industry"),
                "indicatorValue" to plainAny(row, "indicator_value"),
                "valuePrevious" to plainAny(row, "value_previous"),
                "yoyChange" to plainAny(row, "yoy_change"),
            )
        }
        return linkedMapOf(
            "indicatorCode" to indicatorCode,
            "industry" to ind.ifEmpty { null },
            "fiscalYear" to fiscalYear,
            "fiscalPeriod" to fiscalPeriod,
            "count" to list.size,
            "list" to list,
        )
    }

    /**
     * 同行业「某一科目」横向对比（利润表 / 资产负债表 / 现金流量表）：
     * 同一科目 + 同一期间（财年/季度）下，同行业各公司的本期值
     * industry 为空表示不限定行业（比较全部公司）
     */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
    fun getPeerStatementCompare(
        tableType: String,
        itemName: String,
        industry: String?,
        fiscalYear: Int,
        fiscalPeriod: String,
    ): Map<String, Any?> {
        val ind = industry?.trim().orEmpty()
        val rows: List<Tuple> = when (tableType) {
            "income" -> reportRepository.findIncomePeerRows(itemName, fiscalYear, fiscalPeriod, ind)
            "balance" -> reportRepository.findBalancePeerRows(itemName, fiscalYear, fiscalPeriod, ind)
            "cashflow" -> reportRepository.findCashFlowPeerRows(itemName, fiscalYear, fiscalPeriod, ind)
            else -> emptyList()
        }
        val list = rows.map { row ->
            linkedMapOf(
                "companyId" to num(row, "company_id")?.toLong(),
                "companyCode" to str(row, "company_code"),
                "companyName" to str(row, "company_name"),
                "shortName" to str(row, "short_name"),
                "industry" to str(row, "industry"),
                "valueCurrent" to plainAny(row, "value_current"),
                "valuePrevious" to plainAny(row, "value_previous"),
                "unit" to str(row, "unit"),
            )
        }
        return linkedMapOf(
            "tableType" to tableType,
            "itemName" to itemName,
            "industry" to ind.ifEmpty { null },
            "fiscalYear" to fiscalYear,
            "fiscalPeriod" to fiscalPeriod,
            "count" to list.size,
            "list" to list,
        )
    }

    /** 某一科目（利润表/资产负债表/现金流量表之一）的历史走势：该科目在所有财报中的本期值、上期值（财年/期间升序） */
    @Transactional(readOnly = true, transactionManager = "usTransactionManager")
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
                "itemCode" to str(row, "item_code"),
                "itemName" to str(row, "item_name"),
                "itemNameEn" to str(row, "item_name_en"),
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

    // ========== 私有映射 ==========

    private fun companyToMap(c: UsCompany): Map<String, Any?> = linkedMapOf(
        "id" to (c.id ?: 0L),
        "companyCode" to c.ticker,
        "companyName" to c.companyName,
        "shortName" to c.shortName,
        "exchange" to c.exchange,
        "sector" to c.sector,
        "industry" to c.industry,
        "cik" to c.cik,
        "isin" to c.isin,
        "fiscalYearEnd" to c.fiscalYearEnd,
        "country" to c.country,
        "currency" to c.currency,
        "createdAt" to c.createdAt?.toString(),
    )

    private fun reportToMap(r: UsFinancialReport): Map<String, Any?> = linkedMapOf(
        "id" to (r.id ?: 0L),
        "companyId" to r.companyId,
        "reportType" to r.reportType,
        "fiscalYear" to r.fiscalYear,
        "fiscalPeriod" to r.fiscalPeriod,
        "formType" to r.formType,
        "reportDate" to r.reportDate?.toString(),
        "publishDate" to r.publishDate?.toString(),
        "accessionNo" to r.accessionNo,
        "filingUrl" to r.filingUrl,
        "currency" to r.currency,
        "unit" to r.unit,
        "scaleFactor" to r.scaleFactor,
        "auditStatus" to r.auditStatus,
        "parseStatus" to r.parseStatus,
        "parsedAt" to r.parsedAt?.toString(),
        "createdAt" to r.createdAt?.toString(),
    )

    /** 利润表 → Map */
    private fun statementToMap(
        i: UsIncomeStatement,
        activityType: String?,
    ): Map<String, Any?> = statementToMap(
        id = i.id, reportId = i.reportId, itemCode = i.itemCode, itemName = i.itemName,
        itemNameEn = i.itemNameEn, itemLevel = i.itemLevel, parentItem = i.parentItem,
        isTotal = i.isTotal, isSubItem = i.isSubItem, unit = i.unit,
        valueCurrent = i.valueCurrent, valuePrevious = i.valuePrevious,
        sortOrder = i.sortOrder, activityType = activityType,
    )

    /** 资产负债表 → Map */
    private fun statementToMap(
        b: UsBalanceSheet,
        activityType: String?,
    ): Map<String, Any?> = statementToMap(
        id = b.id, reportId = b.reportId, itemCode = b.itemCode, itemName = b.itemName,
        itemNameEn = b.itemNameEn, itemLevel = b.itemLevel, parentItem = b.parentItem,
        isTotal = b.isTotal, isSubItem = b.isSubItem, unit = b.unit,
        valueCurrent = b.valueCurrent, valuePrevious = b.valuePrevious,
        sortOrder = b.sortOrder, activityType = activityType,
    )

    /** 现金流量表 → Map */
    private fun statementToMap(
        c: UsCashFlowStatement,
        activityType: String?,
    ): Map<String, Any?> = statementToMap(
        id = c.id, reportId = c.reportId, itemCode = c.itemCode, itemName = c.itemName,
        itemNameEn = c.itemNameEn, itemLevel = c.itemLevel, parentItem = c.parentItem,
        isTotal = c.isTotal, isSubItem = c.isSubItem, unit = c.unit,
        valueCurrent = c.valueCurrent, valuePrevious = c.valuePrevious,
        sortOrder = c.sortOrder, activityType = activityType,
    )

    /** 三表共有字段 → Map（字段集与 CompanyService 保持一致，便于前端复用同一套渲染） */
    private fun statementToMap(
        id: Long?,
        reportId: Long,
        itemCode: String?,
        itemName: String?,
        itemNameEn: String?,
        itemLevel: Int?,
        parentItem: String?,
        isTotal: Int?,
        isSubItem: Int?,
        unit: String?,
        valueCurrent: BigDecimal?,
        valuePrevious: BigDecimal?,
        sortOrder: Int?,
        activityType: String?,
    ): Map<String, Any?> = linkedMapOf(
        "id" to (id ?: 0L),
        "reportId" to reportId,
        "itemCode" to itemCode,
        "itemName" to itemName,
        "itemNameEn" to itemNameEn,
        "itemLevel" to itemLevel,
        "parentItem" to parentItem,
        "isTotal" to isTotal,
        "isSubItem" to isSubItem,
        "activityType" to activityType,
        "unit" to unit,
        "valueCurrent" to plain(valueCurrent),
        "valuePrevious" to plain(valuePrevious),
        // 美股库无附注索引列，为保持 JSON 结构一致固定返回 null
        "noteRef" to null,
        "sortOrder" to sortOrder,
    )

    private fun indicatorToMap(v: UsFinancialIndicator): Map<String, Any?> = linkedMapOf(
        "id" to (v.id ?: 0L),
        "reportId" to v.reportId,
        "indicatorCode" to v.indicatorCode,
        "indicatorName" to v.indicatorName,
        "indicatorNameEn" to v.indicatorNameEn,
        "indicatorValue" to plain(v.indicatorValue),
        "valuePrevious" to plain(v.valuePrevious),
        "yoyChange" to plain(v.yoyChange),
        "createdAt" to v.createdAt?.toString(),
    )

    /** 报表行（UNION 连接查询结果）→ Map */
    private fun statementRowToMap(row: Tuple): Map<String, Any?> = linkedMapOf(
        "tableType" to str(row, "table_type"),
        "id" to num(row, "id")?.toLong(),
        "itemCode" to str(row, "item_code"),
        "itemName" to str(row, "item_name"),
        "itemNameEn" to str(row, "item_name_en"),
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

    /** BigDecimal 转字符串（保留精度，便于前端展示） */
    private fun plain(v: BigDecimal?): String? = v?.toPlainString()
}
