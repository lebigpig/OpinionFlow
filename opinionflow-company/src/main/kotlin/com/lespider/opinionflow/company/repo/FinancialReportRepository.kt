package com.lespider.opinionflow.company.repo

import com.lespider.opinionflow.company.domain.FinancialReport
import jakarta.persistence.Tuple
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 财报主表仓库
 * 除常规派生查询外，提供「连接查询」：
 *  1) company JOIN financial_report —— 公司 + 其全部财报（并统计各表行数）
 *  2) income_statement UNION ALL balance_sheet UNION ALL cash_flow_statement UNION ALL financial_indicator_value
 *     —— 一次取某份财报下的「各个表」明细
 */
interface FinancialReportRepository : JpaRepository<FinancialReport, Long> {

    /** 某公司的全部财报（财年/期间倒序） */
    fun findByCompanyIdOrderByFiscalYearDescFiscalPeriodDescIdDesc(companyId: Long): List<FinancialReport>

    /** 某公司指定财年的财报 */
    fun findByCompanyIdAndFiscalYearOrderByFiscalPeriodAsc(companyId: Long, fiscalYear: Int): List<FinancialReport>

    /** 某报告的解析状态统计用（按解析状态查询） */
    fun findByParseStatusOrderByIdAsc(parseStatus: String): List<FinancialReport>

    /**
     * 【连接查询 1】公司详情：company LEFT JOIN financial_report
     * 一行 = 公司 + 一份财报；同时用子查询统计该财报下各表行数。
     * 若公司无财报，report_* 与 count 列返回 null / 0。
     */
    @Query(
        value = """
            SELECT
                c.id                                                AS company_id,
                c.company_code                                      AS company_code,
                c.company_name                                      AS company_name,
                c.short_name                                        AS short_name,
                c.exchange                                          AS exchange,
                c.industry                                          AS industry,
                c.fiscal_year_end                                   AS fiscal_year_end,
                r.id                                                AS report_id,
                r.report_type                                       AS report_type,
                r.fiscal_year                                       AS fiscal_year,
                r.fiscal_period                                     AS fiscal_period,
                r.report_date                                       AS report_date,
                r.publish_date                                      AS publish_date,
                r.currency                                          AS currency,
                r.unit                                              AS unit,
                r.audit_status                                      AS audit_status,
                r.parse_status                                      AS parse_status,
                (SELECT COUNT(*) FROM income_statement i WHERE i.report_id = r.id)          AS income_count,
                (SELECT COUNT(*) FROM balance_sheet b WHERE b.report_id = r.id)             AS balance_count,
                (SELECT COUNT(*) FROM cash_flow_statement f WHERE f.report_id = r.id)       AS cashflow_count,
                (SELECT COUNT(*) FROM financial_indicator_value v WHERE v.report_id = r.id) AS indicator_count
            FROM company c
            LEFT JOIN financial_report r ON r.company_id = c.id
            WHERE c.id = :companyId
            ORDER BY r.fiscal_year DESC, r.fiscal_period DESC
        """,
        nativeQuery = true,
    )
    fun findCompanyDetailRows(@Param("companyId") companyId: Long): List<Tuple>

    /**
     * 【连接查询 2】各表明细：利润表 UNION ALL 资产负债表 UNION ALL 现金流量表 UNION ALL 财务指标
     * 统一列结构，用 table_type 区分来源表，便于前端按表分组展示。
     */
    @Query(
        value = """
            SELECT 'income' AS table_type, i.id AS id, i.item_name AS item_name, i.item_level AS item_level,
                   i.parent_item AS parent_item, i.is_total AS is_total, i.is_sub_item AS is_sub_item,
                   NULL AS activity_type, i.unit AS unit, i.value_current AS value_current,
                   i.value_previous AS value_previous, i.note_ref AS note_ref, i.sort_order AS sort_order,
                   NULL AS yoy_change
            FROM income_statement i WHERE i.report_id = :reportId
            UNION ALL
            SELECT 'balance' AS table_type, b.id AS id, b.item_name AS item_name, b.item_level AS item_level,
                   b.parent_item AS parent_item, b.is_total AS is_total, b.is_sub_item AS is_sub_item,
                   NULL AS activity_type, b.unit AS unit, b.value_current AS value_current,
                   b.value_previous AS value_previous, b.note_ref AS note_ref, b.sort_order AS sort_order,
                   NULL AS yoy_change
            FROM balance_sheet b WHERE b.report_id = :reportId
            UNION ALL
            SELECT 'cashflow' AS table_type, f.id AS id, f.item_name AS item_name, f.item_level AS item_level,
                   f.parent_item AS parent_item, f.is_total AS is_total, f.is_sub_item AS is_sub_item,
                   f.activity_type AS activity_type, f.unit AS unit, f.value_current AS value_current,
                   f.value_previous AS value_previous, f.note_ref AS note_ref, f.sort_order AS sort_order,
                   NULL AS yoy_change
            FROM cash_flow_statement f WHERE f.report_id = :reportId
            UNION ALL
            SELECT 'indicator' AS table_type, v.id AS id, v.indicator_name AS item_name, NULL AS item_level,
                   v.indicator_code AS parent_item, NULL AS is_total, NULL AS is_sub_item,
                   NULL AS activity_type, NULL AS unit, v.indicator_value AS value_current,
                   v.value_previous AS value_previous, NULL AS note_ref, NULL AS sort_order,
                   v.yoy_change AS yoy_change
            FROM financial_indicator_value v WHERE v.report_id = :reportId
            ORDER BY table_type, sort_order, id
        """,
        nativeQuery = true,
    )
    fun findStatementUnionRows(@Param("reportId") reportId: Long): List<Tuple>

    /**
     * 【连接查询 3】某指标的历史走势：
     * financial_indicator_value JOIN financial_report JOIN company
     * 返回该指标在全部财报（各季度/年度）中的本期值、上期值、同比，
     * 按财年、期间（Q1<Q2<H1<Q3<Q4<FY）升序，天然满足"从左往右排列历史"。
     */
    @Query(
        value = """
            SELECT
                r.fiscal_year                                    AS fiscal_year,
                r.fiscal_period                                  AS fiscal_period,
                r.report_type                                    AS report_type,
                v.indicator_code                                 AS indicator_code,
                v.indicator_name                                 AS indicator_name,
                v.indicator_value                                AS indicator_value,
                v.value_previous                                 AS value_previous,
                v.yoy_change                                     AS yoy_change
            FROM financial_indicator_value v
            JOIN financial_report r ON r.id = v.report_id
            JOIN company c ON c.id = r.company_id
            WHERE c.id = :companyId AND v.indicator_code = :indicatorCode
            ORDER BY r.fiscal_year ASC,
                CASE r.fiscal_period
                    WHEN 'Q1' THEN 1 WHEN 'Q2' THEN 2 WHEN 'H1' THEN 3
                    WHEN 'Q3' THEN 4 WHEN 'Q4' THEN 5 WHEN 'FY' THEN 6
                    ELSE 99 END ASC,
                r.id ASC
        """,
        nativeQuery = true,
    )
    fun findIndicatorHistoryRows(
        @Param("companyId") companyId: Long,
        @Param("indicatorCode") indicatorCode: String,
    ): List<Tuple>

    /**
     * 【连接查询 4】利润表某一科目的历史走势：
     * income_statement JOIN financial_report JOIN company，
     * 返回该科目在全部财报中的本期值、上期值，按财年、期间（Q1<Q2<H1<Q3<Q4<FY）升序。
     */
    @Query(
        value = """
            SELECT
                r.fiscal_year                                    AS fiscal_year,
                r.fiscal_period                                  AS fiscal_period,
                r.report_type                                    AS report_type,
                s.item_name                                      AS item_name,
                s.unit                                           AS unit,
                s.value_current                                  AS value_current,
                s.value_previous                                 AS value_previous
            FROM income_statement s
            JOIN financial_report r ON r.id = s.report_id
            JOIN company c ON c.id = r.company_id
            WHERE c.id = :companyId AND s.item_name = :itemName
            ORDER BY r.fiscal_year ASC,
                CASE r.fiscal_period
                    WHEN 'Q1' THEN 1 WHEN 'Q2' THEN 2 WHEN 'H1' THEN 3
                    WHEN 'Q3' THEN 4 WHEN 'Q4' THEN 5 WHEN 'FY' THEN 6
                    ELSE 99 END ASC,
                s.sort_order ASC, s.id ASC
        """,
        nativeQuery = true,
    )
    fun findIncomeHistoryRows(
        @Param("companyId") companyId: Long,
        @Param("itemName") itemName: String,
    ): List<Tuple>

    /**
     * 【连接查询 5】资产负债表某一科目的历史走势（结构同 income）。
     */
    @Query(
        value = """
            SELECT
                r.fiscal_year                                    AS fiscal_year,
                r.fiscal_period                                  AS fiscal_period,
                r.report_type                                    AS report_type,
                s.item_name                                      AS item_name,
                s.unit                                           AS unit,
                s.value_current                                  AS value_current,
                s.value_previous                                 AS value_previous
            FROM balance_sheet s
            JOIN financial_report r ON r.id = s.report_id
            JOIN company c ON c.id = r.company_id
            WHERE c.id = :companyId AND s.item_name = :itemName
            ORDER BY r.fiscal_year ASC,
                CASE r.fiscal_period
                    WHEN 'Q1' THEN 1 WHEN 'Q2' THEN 2 WHEN 'H1' THEN 3
                    WHEN 'Q3' THEN 4 WHEN 'Q4' THEN 5 WHEN 'FY' THEN 6
                    ELSE 99 END ASC,
                s.sort_order ASC, s.id ASC
        """,
        nativeQuery = true,
    )
    fun findBalanceHistoryRows(
        @Param("companyId") companyId: Long,
        @Param("itemName") itemName: String,
    ): List<Tuple>

    /**
     * 【连接查询 6】现金流量表某一科目的历史走势（结构同 income）。
     */
    @Query(
        value = """
            SELECT
                r.fiscal_year                                    AS fiscal_year,
                r.fiscal_period                                  AS fiscal_period,
                r.report_type                                    AS report_type,
                s.item_name                                      AS item_name,
                s.unit                                           AS unit,
                s.value_current                                  AS value_current,
                s.value_previous                                 AS value_previous
            FROM cash_flow_statement s
            JOIN financial_report r ON r.id = s.report_id
            JOIN company c ON c.id = r.company_id
            WHERE c.id = :companyId AND s.item_name = :itemName
            ORDER BY r.fiscal_year ASC,
                CASE r.fiscal_period
                    WHEN 'Q1' THEN 1 WHEN 'Q2' THEN 2 WHEN 'H1' THEN 3
                    WHEN 'Q3' THEN 4 WHEN 'Q4' THEN 5 WHEN 'FY' THEN 6
                    ELSE 99 END ASC,
                s.sort_order ASC, s.id ASC
        """,
        nativeQuery = true,
    )
    fun findCashFlowHistoryRows(
        @Param("companyId") companyId: Long,
        @Param("itemName") itemName: String,
    ): List<Tuple>
}