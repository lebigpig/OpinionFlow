package com.lespider.opinionflow.repo

import com.lespider.opinionflow.domain.NewYorkNews
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface NewYorkNewsRepository : JpaRepository<NewYorkNews, Long> {
    @Query(
        value = "select n from NewYorkNews n where (:start is null or n.publishDate >= :start) and (:end is null or n.publishDate <= :end) and (:q is null or (n.title like concat('%', :q, '%') or n.summary like concat('%', :q, '%'))) order by n.publishDate desc, n.id desc",
        countQuery = "select count(n) from NewYorkNews n where (:start is null or n.publishDate >= :start) and (:end is null or n.publishDate <= :end) and (:q is null or (n.title like concat('%', :q, '%') or n.summary like concat('%', :q, '%')))",
    )
    fun pageFiltered(
        @Param("start") start: String?,
        @Param("end") end: String?,
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<NewYorkNews>
}

