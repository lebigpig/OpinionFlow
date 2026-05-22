package com.lespider.opinionflow.rag.repo

import com.lespider.opinionflow.rag.domain.WyNews
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface WyNewsRepository : JpaRepository<WyNews, Long> {
    fun findByIdAfter(id: Long, pageable: Pageable): Page<WyNews>
}