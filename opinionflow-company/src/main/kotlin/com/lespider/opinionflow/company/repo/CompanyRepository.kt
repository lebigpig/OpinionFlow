package com.lespider.opinionflow.company.repo

import com.lespider.opinionflow.company.domain.Company
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 公司主表仓库
 */
interface CompanyRepository : JpaRepository<Company, Long> {

    /** 按关键字模糊搜索（股票代码 或 公司名称，忽略大小写） */
    fun findByCompanyCodeContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(
        companyCode: String,
        companyName: String,
        pageable: Pageable,
    ): Page<Company>

    /** 全部公司（按股票代码升序） */
    fun findAllByOrderByCompanyCodeAsc(): List<Company>
}