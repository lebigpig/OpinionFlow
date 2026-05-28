package com.lespider.opinionflow.spider.service

import com.lespider.opinionflow.spider.domain.SearchResult
import com.lespider.opinionflow.spider.dto.SearchResultItem
import com.lespider.opinionflow.spider.repo.SearchResultRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Service

@Service
class SearchResultService(
    private val searchResultRepository: SearchResultRepository,
) {
    fun saveResults(items: List<SearchResultItem>): List<SearchResult> {
        val now = LocalDateTime.now()
        val entities = items.map { item ->
            SearchResult(
                query = item.query,
                url = item.url,
                title = item.title,
                score = item.score,
                publishedDate = item.publishedDate?.let { parseDate(it) },
                content = item.content,
                rawContent = item.rawContent,
                createdAt = now,
            )
        }
        return searchResultRepository.saveAll(entities)
    }

    private fun parseDate(dateStr: String): LocalDateTime? {
        return try {
            LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (_: Exception) {
                null
            }
        }
    }
}