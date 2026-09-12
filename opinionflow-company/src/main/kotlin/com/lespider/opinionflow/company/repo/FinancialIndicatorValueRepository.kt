package com.lespider.opinionflow.company.repo

import com.lespider.opinionflow.company.domain.FinancialIndicatorValue
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 财务指标值仓库
 */
interface FinancialIndicatorValueRepository : JpaRepository<FinancialIndicatorValue, Long> {

    /** 某报告下的财务指标（按指标编码升序） */
    fun findByReportIdOrderByIndicatorCodeAsc(reportId: Long): List<FinancialIndicatorValue>
}