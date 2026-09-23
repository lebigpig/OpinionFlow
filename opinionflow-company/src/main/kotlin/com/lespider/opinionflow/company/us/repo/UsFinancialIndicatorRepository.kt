package com.lespider.opinionflow.company.us.repo

import com.lespider.opinionflow.company.us.domain.UsFinancialIndicator
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 美股财务指标值仓库（company_us 库）
 */
interface UsFinancialIndicatorRepository : JpaRepository<UsFinancialIndicator, Long> {

    /** 某报告下的财务指标（按指标编码升序） */
    fun findByReportIdOrderByIndicatorCodeAsc(reportId: Long): List<UsFinancialIndicator>
}
