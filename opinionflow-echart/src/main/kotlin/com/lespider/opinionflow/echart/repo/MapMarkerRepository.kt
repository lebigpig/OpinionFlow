package com.lespider.opinionflow.echart.repo

import com.lespider.opinionflow.echart.domain.MapMarker
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MapMarkerRepository : JpaRepository<MapMarker, Long> {

    /** 按创建时间降序查询所有标记 */
    fun findAllByOrderByCreatedAtDesc(): List<MapMarker>
}
