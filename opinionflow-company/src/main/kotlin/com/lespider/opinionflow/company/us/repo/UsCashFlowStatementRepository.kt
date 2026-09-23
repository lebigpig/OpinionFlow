package com.lespider.opinionflow.company.us.repo

import com.lespider.opinionflow.company.us.domain.UsCashFlowStatement
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 美股现金流量表明细仓库（company_us 库）
 */
interface UsCashFlowStatementRepository : JpaRepository<UsCashFlowStatement, Long> {

    /** 某报告下的现金流量表明细（按排序号升序） */
    fun findByReportIdOrderBySortOrderAscIdAsc(reportId: Long): List<UsCashFlowStatement>
}
