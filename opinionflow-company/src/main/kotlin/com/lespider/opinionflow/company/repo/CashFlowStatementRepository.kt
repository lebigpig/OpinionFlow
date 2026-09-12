package com.lespider.opinionflow.company.repo

import com.lespider.opinionflow.company.domain.CashFlowStatement
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 现金流量表明细仓库
 */
interface CashFlowStatementRepository : JpaRepository<CashFlowStatement, Long> {

    /** 某报告下的现金流量表明细（按排序号升序） */
    fun findByReportIdOrderBySortOrderAscIdAsc(reportId: Long): List<CashFlowStatement>
}