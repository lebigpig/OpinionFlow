package com.lespider.opinionflow.repo

import com.lespider.opinionflow.domain.WyNews
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface WyNewsRepository : JpaRepository<WyNews, Long> {
    @Query(
        value = "select w from WyNews w where w.title in :titles order by w.publishTime desc, w.id desc",
        countQuery = "select count(w) from WyNews w where w.title in :titles",
    )
    fun pageByTitles(
        @Param("titles") titles: Collection<String>,
        pageable: Pageable,
    ): Page<WyNews>

    @Query(
        value = "select w from WyNews w where w.title in :titles and (:start is null or w.publishTime >= :start) and (:end is null or w.publishTime <= :end) order by w.publishTime desc, w.id desc",
        countQuery = "select count(w) from WyNews w where w.title in :titles and (:start is null or w.publishTime >= :start) and (:end is null or w.publishTime <= :end)",
    )
    fun pageByTitlesInTimeRange(
        @Param("titles") titles: Collection<String>,
        @Param("start") start: LocalDateTime?,
        @Param("end") end: LocalDateTime?,
        pageable: Pageable,
    ): Page<WyNews>

    @Query(
        value = "select w from WyNews w where w.title in :titles and (:start is null or w.publishTime >= :start) and (:end is null or w.publishTime <= :end) and (:q is null or (w.title like concat('%', :q, '%') or w.content like concat('%', :q, '%'))) order by w.publishTime desc, w.id desc",
        countQuery = "select count(w) from WyNews w where w.title in :titles and (:start is null or w.publishTime >= :start) and (:end is null or w.publishTime <= :end) and (:q is null or (w.title like concat('%', :q, '%') or w.content like concat('%', :q, '%')))",
    )
    fun pageByTitlesFiltered(
        @Param("titles") titles: Collection<String>,
        @Param("start") start: LocalDateTime?,
        @Param("end") end: LocalDateTime?,
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<WyNews>

    @Query(
        value = "select w from WyNews w where w.title is null or w.title not in :titles order by w.publishTime desc, w.id desc",
        countQuery = "select count(w) from WyNews w where w.title is null or w.title not in :titles",
    )
    fun pageExcludingTitles(
        @Param("titles") titles: Collection<String>,
        pageable: Pageable,
    ): Page<WyNews>

    @Query(
        value = "select w from WyNews w where (w.title is null or w.title not in :titles) and (:start is null or w.publishTime >= :start) and (:end is null or w.publishTime <= :end) order by w.publishTime desc, w.id desc",
        countQuery = "select count(w) from WyNews w where (w.title is null or w.title not in :titles) and (:start is null or w.publishTime >= :start) and (:end is null or w.publishTime <= :end)",
    )
    fun pageExcludingTitlesInTimeRange(
        @Param("titles") titles: Collection<String>,
        @Param("start") start: LocalDateTime?,
        @Param("end") end: LocalDateTime?,
        pageable: Pageable,
    ): Page<WyNews>

    @Query(
        value = "select w from WyNews w where (w.title is null or w.title not in :titles) and (:start is null or w.publishTime >= :start) and (:end is null or w.publishTime <= :end) and (:q is null or (w.title like concat('%', :q, '%') or w.content like concat('%', :q, '%'))) order by w.publishTime desc, w.id desc",
        countQuery = "select count(w) from WyNews w where (w.title is null or w.title not in :titles) and (:start is null or w.publishTime >= :start) and (:end is null or w.publishTime <= :end) and (:q is null or (w.title like concat('%', :q, '%') or w.content like concat('%', :q, '%')))",
    )
    fun pageExcludingTitlesFiltered(
        @Param("titles") titles: Collection<String>,
        @Param("start") start: LocalDateTime?,
        @Param("end") end: LocalDateTime?,
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<WyNews>

    /** 增量查询：id 大于指定值，按 id 升序分页 */
    @Query(
        value = "select w from WyNews w where w.id > :afterId order by w.id asc",
        countQuery = "select count(w) from WyNews w where w.id > :afterId",
    )
    fun findByIdAfter(
        @Param("afterId") afterId: Long,
        pageable: Pageable,
    ): Page<WyNews>
}
