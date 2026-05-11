package com.lespider.opinionflow.service

import com.lespider.opinionflow.domain.NewsConstants
import com.lespider.opinionflow.repo.FalshNewsRepository
import com.lespider.opinionflow.repo.WyNewsRepository
import com.lespider.opinionflow.web.dto.NewsDetailDto
import com.lespider.opinionflow.web.dto.IdListResponse
import com.lespider.opinionflow.web.dto.NewsSummaryDto
import com.lespider.opinionflow.web.dto.PageDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class NewsService(
    private val wyNewsRepository: WyNewsRepository,
    private val falshNewsRepository: FalshNewsRepository,
) {
    private val ymdHms: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @Transactional(readOnly = true)
    fun pageDeepseekMenu(page: Int, size: Int, start: String?, end: String?, q: String?): PageDto<NewsSummaryDto> {
        val p = page.coerceAtLeast(0)
        val s = size.coerceIn(1, 200)
        val startDt = parseDateTimeOrNull(start)
        val endDt = parseDateTimeOrNull(end)
        val qq = q?.trim()?.takeIf { it.isNotEmpty() }
        val result = wyNewsRepository.pageByTitlesFiltered(
            NewsConstants.DEEPSEEK_MENU_TITLES,
            startDt,
            endDt,
            qq,
            PageRequest.of(p, s),
        )
        return PageDto(
            content = result.content.map { it.toSummary() },
            page = p,
            size = s,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun pageGeneral(page: Int, size: Int, start: String?, end: String?, q: String?): PageDto<NewsSummaryDto> {
        val p = page.coerceAtLeast(0)
        val s = size.coerceIn(1, 200)
        val startDt = parseDateTimeOrNull(start)
        val endDt = parseDateTimeOrNull(end)
        val qq = q?.trim()?.takeIf { it.isNotEmpty() }
        val result = wyNewsRepository.pageExcludingTitlesFiltered(
            NewsConstants.DEEPSEEK_MENU_TITLES,
            startDt,
            endDt,
            qq,
            PageRequest.of(p, s),
        )
        return PageDto(
            content = result.content.map { it.toSummary() },
            page = p,
            size = s,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun getById(id: Long): NewsDetailDto {
        val entity = wyNewsRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return NewsDetailDto(
            id = entity.id ?: throw ResponseStatusException(HttpStatus.NOT_FOUND),
            title = entity.title,
            content = entity.content,
            publishTime = entity.publishTime,
        )
    }

    @Transactional(readOnly = true)
    fun pageFinance(page: Int, size: Int, start: String?, end: String?, q: String?): PageDto<NewsSummaryDto> {
        val p = page.coerceAtLeast(0)
        val s = size.coerceIn(1, 200)
        val startStr = normalizeFinanceSend(start)
        val endStr = normalizeFinanceSend(end)
        val qq = q?.trim()?.takeIf { it.isNotEmpty() }
        val result = falshNewsRepository.pageAllFiltered(startStr, endStr, qq, PageRequest.of(p, s))
        return PageDto(
            content = result.content.map {
                NewsSummaryDto(
                    id = checkNotNull(it.getId()) { "falsh_news 主键为空" },
                    title = it.getSend(),
                    publishTime = parseFinanceSendTime(it.getSend()),
                    summary = contentSummary(it.getContent()),
                )
            },
            page = p,
            size = s,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun financeIds(start: String?, end: String?, q: String?, limit: Int?): IdListResponse {
        val startStr = normalizeFinanceSend(start)
        val endStr = normalizeFinanceSend(end)
        val qq = q?.trim()?.takeIf { it.isNotEmpty() }
        val lim = (limit ?: 5000).coerceIn(1, 20000)

        val page = falshNewsRepository.idsFiltered(startStr, endStr, qq, PageRequest.of(0, lim))
        val total = page.totalElements
        val ids = page.content
        val truncated = total > ids.size.toLong()
        return IdListResponse(
            ids = ids,
            total = total,
            truncated = truncated,
            limit = lim,
        )
    }

    @Transactional(readOnly = true)
    fun getFinanceById(id: Long): NewsDetailDto {
        val entity = falshNewsRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return NewsDetailDto(
            id = entity.id ?: throw ResponseStatusException(HttpStatus.NOT_FOUND),
            title = entity.send,
            content = entity.content,
            publishTime = parseFinanceSendTime(entity.send),
        )
    }

    private fun com.lespider.opinionflow.domain.WyNews.toSummary(): NewsSummaryDto =
        NewsSummaryDto(
            id = checkNotNull(id) { "wynews 主键为空" },
            title = title,
            publishTime = publishTime,
        )

    private fun contentSummary(content: String?): String? {
        val text = content?.trim().orEmpty()
        if (text.isEmpty()) return null

        val idx = text.indexOf('。')
        if (idx >= 0) {
            return text.substring(0, idx + 1).trim()
        }

        val max = 120
        return if (text.length <= max) text else text.substring(0, max).trim()
    }

    private fun parseDateTimeOrNull(text: String?): LocalDateTime? {
        val s = text?.trim().orEmpty()
        if (s.isEmpty()) return null
        return try {
            LocalDateTime.parse(s, ymdHms)
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(s) // 兼容 ISO 8601
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun normalizeFinanceSend(text: String?): String? {
        val dt = parseDateTimeOrNull(text) ?: return null
        return dt.format(ymdHms)
    }

    private fun parseFinanceSendTime(send: String?): LocalDateTime? {
        val s = send?.trim().orEmpty()
        if (s.isEmpty()) return null
        return try {
            LocalDateTime.parse(s, ymdHms)
        } catch (_: Exception) {
            null
        }
    }
}
