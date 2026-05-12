package com.lespider.opinionflow.repo

import com.lespider.opinionflow.domain.YahooFinanceNews
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface YahooFinanceNewsRepository : JpaRepository<YahooFinanceNews, String> {
    @Query(
        value = "select y from YahooFinanceNews y where (:start is null or y.displayTime >= :start) and (:end is null or y.displayTime <= :end) and (:q is null or (y.title like concat('%', :q, '%') or y.summary like concat('%', :q, '%'))) order by y.displayTime desc, y.fetchedAt desc, y.id desc",
        countQuery = "select count(y) from YahooFinanceNews y where (:start is null or y.displayTime >= :start) and (:end is null or y.displayTime <= :end) and (:q is null or (y.title like concat('%', :q, '%') or y.summary like concat('%', :q, '%')))",
    )
    fun pageFiltered(
        @Param("start") start: String?,
        @Param("end") end: String?,
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<YahooFinanceNews>

    /** 增量查询：fetched_at 大于指定时间，按 fetched_at 升序分页 */
    @Query(
        value = "select y from YahooFinanceNews y where y.fetchedAt > :afterTime order by y.fetchedAt asc",
        countQuery = "select count(y) from YahooFinanceNews y where y.fetchedAt > :afterTime",
    )
    fun findByFetchedAtAfter(
        @Param("afterTime") afterTime: LocalDateTime,
        pageable: Pageable,
    ): Page<YahooFinanceNews>
}

