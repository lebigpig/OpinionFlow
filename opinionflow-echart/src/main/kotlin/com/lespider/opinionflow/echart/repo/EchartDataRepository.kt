package com.lespider.opinionflow.echart.repo

import com.lespider.opinionflow.echart.domain.EchartData
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EchartDataRepository : JpaRepository<EchartData, Long> {

    /**
     * 按创建时间降序查询所有数据
     */
    fun findAllByOrderByCreatedAtDesc(): List<EchartData>
}