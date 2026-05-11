package com.lespider.opinionflow.service

import com.lespider.opinionflow.repo.YahooFinanceNewsRepository
import com.lespider.opinionflow.web.dto.PageDto
import com.lespider.opinionflow.web.dto.YahooFinanceNewsDto
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class YahooFinanceNewsService(
    private val yahooFinanceNewsRepository: YahooFinanceNewsRepository,
) {
    @Transactional(readOnly = true)
    fun page(page: Int, size: Int, start: String?, end: String?, q: String?): PageDto<YahooFinanceNewsDto> {
        val p = page.coerceAtLeast(0)
        val s = size.coerceIn(1, 200)
        val startStr = start?.trim()?.takeIf { it.isNotEmpty() }
        val endStr = end?.trim()?.takeIf { it.isNotEmpty() }
        val qq = q?.trim()?.takeIf { it.isNotEmpty() }

        val result = yahooFinanceNewsRepository.pageFiltered(startStr, endStr, qq, PageRequest.of(p, s))
        return PageDto(
            content = result.content.map {
                YahooFinanceNewsDto(
                    id = it.id ?: "",
                    title = it.title,
                    summary = it.summary,
                    displayTime = it.displayTime,
                    articleUrl = it.articleUrl,
                    imgUrl = it.imgUrl,
                )
            }.filter { it.id.isNotEmpty() },
            page = p,
            size = s,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }
}

