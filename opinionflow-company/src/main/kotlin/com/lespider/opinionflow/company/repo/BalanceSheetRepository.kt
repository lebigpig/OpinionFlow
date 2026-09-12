package com.lespider.opinionflow.company.repo

import com.lespider.opinionflow.company.domain.BalanceSheet
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 资产负债表明细仓库
 */
interface BalanceSheetRepository : JpaRepository<BalanceSheet, Long> {

    /** 某报告下的资产负债表明细（按排序号升序） */
    fun findByReportIdOrderBySortOrderAscIdAsc(reportId: Long): List<BalanceSheet>
}