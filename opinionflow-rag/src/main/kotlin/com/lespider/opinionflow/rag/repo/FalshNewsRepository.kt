package com.lespider.opinionflow.rag.repo

import com.lespider.opinionflow.rag.domain.FalshNews
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface FalshNewsRepository : JpaRepository<FalshNews, Long> {
    fun findByIdAfter(id: Long, pageable: Pageable): Page<FalshNews>
}