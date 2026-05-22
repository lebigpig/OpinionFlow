package com.lespider.opinionflow.news.service

import com.lespider.opinionflow.common.core.PageResult
import com.lespider.opinionflow.news.domain.YahooFinanceNews
import com.lespider.opinionflow.news.repo.YahooFinanceNewsRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class YahooFinanceNewsService(
    private val yahooFinanceNewsRepository: YahooFinanceNewsRepository,
) {
    @Transactional(readOnly = true)
    fun page(page: Int, size: Int, start: String?, end: String?, q: String?): PageResult<YahooFinanceNews> {
        val p = page.coerceAtLeast(0)
        val s = size.coerceIn(1, 200)
        val startStr = start?.trim()?.takeIf { it.isNotEmpty() }
        val endStr = end?.trim()?.takeIf { it.isNotEmpty() }
        val qq = q?.trim()?.takeIf { it.isNotEmpty() }

        val result = yahooFinanceNewsRepository.pageFiltered(startStr, endStr, qq, PageRequest.of(p, s))
        return PageResult(
            list = result.content,
            page = p,
            size = s,
            total = result.totalElements,
        )
    }
}