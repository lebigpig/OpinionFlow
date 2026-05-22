package com.lespider.opinionflow.rag.repo

import com.lespider.opinionflow.rag.domain.YahooFinanceNews
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface YahooFinanceNewsRepository : JpaRepository<YahooFinanceNews, String> {
    fun findByFetchedAtAfter(fetchedAt: LocalDateTime, pageable: Pageable): Page<YahooFinanceNews>
}