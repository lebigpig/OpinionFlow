package com.lespider.opinionflow.company.us.repo

import com.lespider.opinionflow.company.us.domain.UsIncomeStatement
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 美股利润表明细仓库（company_us 库）
 */
interface UsIncomeStatementRepository : JpaRepository<UsIncomeStatement, Long> {

    /** 某报告下的利润表明细（按排序号升序） */
    fun findByReportIdOrderBySortOrderAscIdAsc(reportId: Long): List<UsIncomeStatement>
}
