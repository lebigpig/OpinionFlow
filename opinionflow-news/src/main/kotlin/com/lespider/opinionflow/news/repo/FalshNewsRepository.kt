package com.lespider.opinionflow.news.repo

import com.lespider.opinionflow.news.domain.FalshNews
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FalshNewsRepository : JpaRepository<FalshNews, Long> {
    interface FalshNewsListRow {
        fun getId(): Long?
        fun getSend(): String?
        fun getContent(): String?
    }

    @Query(
        value = "select f.id as id, f.send as send, f.content as content from FalshNews f order by f.send desc, f.id desc",
        countQuery = "select count(f) from FalshNews f",
    )
    fun pageAll(pageable: Pageable): Page<FalshNewsListRow>

    @Query(
        value = "select f.id as id, f.send as send, f.content as content from FalshNews f where (:start is null or f.send >= :start) and (:end is null or f.send <= :end) order by f.send desc, f.id desc",
        countQuery = "select count(f) from FalshNews f where (:start is null or f.send >= :start) and (:end is null or f.send <= :end)",
    )
    fun pageAllInSendRange(
        @Param("start") start: String?,
        @Param("end") end: String?,
        pageable: Pageable,
    ): Page<FalshNewsListRow>

    @Query(
        value = "select f.id as id, f.send as send, f.content as content from falsh_news f where (:start is null or f.send >= :start) and (:end is null or f.send <= :end) and (:q is null or :q = '' or MATCH(f.content) AGAINST(:q IN NATURAL LANGUAGE MODE)) order by f.send desc, f.id desc",
        countQuery = "select count(*) from falsh_news f where (:start is null or f.send >= :start) and (:end is null or f.send <= :end) and (:q is null or :q = '' or MATCH(f.content) AGAINST(:q IN NATURAL LANGUAGE MODE))",
        nativeQuery = true,
    )
    fun pageAllFiltered(
        @Param("start") start: String?,
        @Param("end") end: String?,
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<FalshNewsListRow>

    @Query(
        value = "select f.id from falsh_news f where (:start is null or f.send >= :start) and (:end is null or f.send <= :end) and (:q is null or :q = '' or MATCH(f.content) AGAINST(:q IN NATURAL LANGUAGE MODE)) order by f.send desc, f.id desc",
        countQuery = "select count(*) from falsh_news f where (:start is null or f.send >= :start) and (:end is null or f.send <= :end) and (:q is null or :q = '' or MATCH(f.content) AGAINST(:q IN NATURAL LANGUAGE MODE))",
        nativeQuery = true,
    )
    fun idsFiltered(
        @Param("start") start: String?,
        @Param("end") end: String?,
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<Long>

    /** 增量查询：id 大于指定值，按 id 升序分页 */
    @Query(
        value = "select f from FalshNews f where f.id > :afterId order by f.id asc",
        countQuery = "select count(f) from FalshNews f where f.id > :afterId",
    )
    fun findByIdAfter(
        @Param("afterId") afterId: Long,
        pageable: Pageable,
    ): Page<FalshNews>
}