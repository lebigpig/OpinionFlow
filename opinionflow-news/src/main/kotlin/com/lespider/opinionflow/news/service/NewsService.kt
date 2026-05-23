package com.lespider.opinionflow.news.service

import com.lespider.opinionflow.news.domain.NewsConstants
import com.lespider.opinionflow.news.repo.FalshNewsRepository
import com.lespider.opinionflow.news.repo.WyNewsRepository
import com.lespider.opinionflow.news.repo.YahooFinanceNewsRepository
import com.lespider.opinionflow.news.dto.NewsDetailDto
import com.lespider.opinionflow.news.dto.IdListResponse
import com.lespider.opinionflow.news.dto.NewsSummaryDto
import com.lespider.opinionflow.news.dto.PageDto
import com.lespider.opinionflow.news.domain.WyNews
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
    private val yahooFinanceNewsRepository: YahooFinanceNewsRepository,
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
            id = checkNotNull(entity.id),
            title = entity.title,
            content = entity.content,
            time = entity.time,
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
                    time = parseFinanceSendTime(it.getSend()),
                    content = it.getContent(),
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
            ids = ids.map { it.toString() },
            total = total,
            truncated = truncated,
            limit = lim,
        )
    }

    @Transactional(readOnly = true)
    fun generalIds(start: String?, end: String?, q: String?, limit: Int?): IdListResponse {
        val startDt = parseDateTimeOrNull(start)
        val endDt = parseDateTimeOrNull(end)
        val qq = q?.trim()?.takeIf { it.isNotEmpty() }
        val lim = (limit ?: 5000).coerceIn(1, 50000)

        // 通用新闻排除 DeepSeek 菜单标题
        val allCount = wyNewsRepository.countFiltered(startDt, endDt, qq)
        val ids = wyNewsRepository.findIdsFiltered(startDt, endDt, qq, PageRequest.of(0, lim))
        val truncated = allCount > ids.size.toLong()
        return IdListResponse(
            ids = ids.map { it.toString() },
            total = allCount,
            truncated = truncated,
            limit = lim,
        )
    }

    @Transactional(readOnly = true)
    fun yahooIds(start: String?, end: String?, q: String?, limit: Int?): IdListResponse {
        val qq = q?.trim()?.takeIf { it.isNotEmpty() }
        val lim = (limit ?: 5000).coerceIn(1, 50000)
        val allCount = yahooFinanceNewsRepository.countFiltered(start, end, qq)
        val ids = yahooFinanceNewsRepository.findIdsFiltered(start, end, qq, PageRequest.of(0, lim))
        val truncated = allCount > ids.size.toLong()
        return IdListResponse(
            ids = ids,
            total = allCount,
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
            time = parseFinanceSendTime(entity.send),
        )
    }

    private fun WyNews.toSummary(): NewsSummaryDto =
        NewsSummaryDto(
            id = checkNotNull(id) { "wynews 主键为空" },
            title = title,
            time = time,
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