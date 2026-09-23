package com.lespider.opinionflow.company.us.repo

import com.lespider.opinionflow.company.us.domain.UsBalanceSheet
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 美股资产负债表明细仓库（company_us 库）
 */
interface UsBalanceSheetRepository : JpaRepository<UsBalanceSheet, Long> {

    /** 某报告下的资产负债表明细（按排序号升序） */
    fun findByReportIdOrderBySortOrderAscIdAsc(reportId: Long): List<UsBalanceSheet>
}
