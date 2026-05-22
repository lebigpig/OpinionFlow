package com.lespider.opinionflow.spider.repo

import com.lespider.opinionflow.spider.domain.StockComment
import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StockCommentRepository : JpaRepository<StockComment, Long> {
    @Query(
        value = "select s from StockComment s where (:start is null or s.analysisTime >= :start) and (:end is null or s.analysisTime <= :end) and (:q is null or s.stockCode like concat('%', :q, '%')) order by s.analysisTime desc, s.id desc",
        countQuery = "select count(s) from StockComment s where (:start is null or s.analysisTime >= :start) and (:end is null or s.analysisTime <= :end) and (:q is null or s.stockCode like concat('%', :q, '%'))",
    )
    fun pageFiltered(
        @Param("start") start: LocalDateTime?,
        @Param("end") end: LocalDateTime?,
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<StockComment>
}