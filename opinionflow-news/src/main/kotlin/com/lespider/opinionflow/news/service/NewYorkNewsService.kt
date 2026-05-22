package com.lespider.opinionflow.news.service

import com.lespider.opinionflow.news.repo.NewYorkNewsRepository
import com.lespider.opinionflow.news.dto.NewYorkNewsDto
import com.lespider.opinionflow.news.dto.PageDto
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NewYorkNewsService(
    private val newYorkNewsRepository: NewYorkNewsRepository,
) {
    @Transactional(readOnly = true)
    fun page(page: Int, size: Int, start: String?, end: String?, q: String?): PageDto<NewYorkNewsDto> {
        val p = page.coerceAtLeast(0)
        val s = size.coerceIn(1, 200)
        val startStr = start?.trim()?.takeIf { it.isNotEmpty() }
        val endStr = end?.trim()?.takeIf { it.isNotEmpty() }
        val qq = q?.trim()?.takeIf { it.isNotEmpty() }

        val result = newYorkNewsRepository.pageFiltered(startStr, endStr, qq, PageRequest.of(p, s))
        return PageDto(
            content = result.content.map {
                NewYorkNewsDto(
                    id = it.id?.toString().orEmpty(),
                    title = it.title,
                    summary = it.summary,
                    displayTime = it.publishDate,
                    articleUrl = it.link,
                    imgUrl = it.img,
                )
            }.filter { it.id.isNotEmpty() },
            page = p,
            size = s,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }
}