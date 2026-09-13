package com.lespider.opinionflow.company.repo

import com.lespider.opinionflow.company.domain.Company
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query

/**
 * 公司主表仓库
 * 继承 JpaSpecificationExecutor 以支持「关键字 + 行业筛选 + 动态排序」组合查询
 */
interface CompanyRepository : JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {

    /** 按关键字模糊搜索（股票代码 或 公司名称，忽略大小写） */
    fun findByCompanyCodeContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(
        companyCode: String,
        companyName: String,
        pageable: Pageable,
    ): Page<Company>

    /** 全部公司（按股票代码升序） */
    fun findAllByOrderByCompanyCodeAsc(): List<Company>

    /** 全部所属行业（去重、升序，供前端下拉框选择） */
    @Query("select distinct c.industry from Company c where c.industry is not null and c.industry <> '' order by c.industry asc")
    fun findDistinctIndustries(): List<String>
}