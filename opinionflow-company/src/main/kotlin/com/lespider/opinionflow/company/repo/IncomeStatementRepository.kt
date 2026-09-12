package com.lespider.opinionflow.company.repo

import com.lespider.opinionflow.company.domain.IncomeStatement
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 利润表明细仓库
 */
interface IncomeStatementRepository : JpaRepository<IncomeStatement, Long> {

    /** 某报告下的利润表明细（按排序号升序） */
    fun findByReportIdOrderBySortOrderAscIdAsc(reportId: Long): List<IncomeStatement>
}