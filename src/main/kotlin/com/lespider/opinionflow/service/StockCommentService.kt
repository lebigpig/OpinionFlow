package com.lespider.opinionflow.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.lespider.opinionflow.domain.StockComment
import com.lespider.opinionflow.repo.StockCommentRepository
import com.lespider.opinionflow.web.dto.PageDto
import com.lespider.opinionflow.web.dto.StockCommentDetailDto
import com.lespider.opinionflow.web.dto.StockCommentSummaryDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class StockCommentService(
    private val stockCommentRepository: StockCommentRepository,
    private val objectMapper: ObjectMapper,
) {
    private val ymdHms: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @Transactional(readOnly = true)
    fun pageComments(page: Int, size: Int, start: String?, end: String?, q: String?): PageDto<StockCommentSummaryDto> {
        val p = page.coerceAtLeast(0)
        val s = size.coerceIn(1, 200)
        val startDt = parseDateTimeOrNull(start)
        val endDt = parseDateTimeOrNull(end)
        val qq = q?.trim()?.takeIf { it.isNotEmpty() }

        val result = stockCommentRepository.pageFiltered(startDt, endDt, qq, PageRequest.of(p, s))
        return PageDto(
            content = result.content.map {
                StockCommentSummaryDto(
                    id = checkNotNull(it.id) { "stock_comment 主键为空" },
                    stockCode = it.stockCode,
                    analysisTime = it.analysisTime,
                    totalCommentsAnalyzed = it.total,
                )
            },
            page = p,
            size = s,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun getCommentById(id: Long): StockCommentDetailDto {
        val e = stockCommentRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return toDetailDto(e)
    }

    private fun toDetailDto(e: StockComment): StockCommentDetailDto =
        StockCommentDetailDto(
            id = checkNotNull(e.id) { "stock_comment 主键为空" },
            stockCode = e.stockCode,
            analysisTime = e.analysisTime,
            totalCommentsAnalyzed = e.total,
            mood = e.mood,
            ivi = e.ivi,
            narrativeCoherence = e.narrativeCoherence,
            mainThemes = parseMainThemes(e.mainThemesJson),
            mainThemesContent = e.mainThemesContent,
            themeCount = e.themeCount,
            infoSourceReliance = e.infoSourceReliance,
        )

    private fun parseMainThemes(json: String?): Map<String, Double> {
        val s = json?.trim().orEmpty()
        if (s.isEmpty()) return emptyMap()
        return try {
            when (s.firstOrNull()) {
                '{' -> objectMapper.readValue(s, object : TypeReference<Map<String, Double>>() {})
                    .filterKeys { it.isNotBlank() }
                    .mapValues { (_, v) ->
                        val n = v
                        if (n.isNaN() || n.isInfinite()) 0.0 else n.coerceIn(0.0, 1.0)
                    }
                '[' -> {
                    // 兼容旧格式：["主题A","主题B"] => {"主题A":1,"主题B":1}
                    val list = objectMapper.readValue(s, object : TypeReference<List<String>>() {})
                    list.mapNotNull { it?.trim()?.takeIf { x -> x.isNotEmpty() } }
                        .associateWith { 1.0 }
                }
                else -> emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseDateTimeOrNull(text: String?): LocalDateTime? {
        val s = text?.trim().orEmpty()
        if (s.isEmpty()) return null
        return try {
            LocalDateTime.parse(s, ymdHms)
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(s)
            } catch (_: Exception) {
                null
            }
        }
    }
}

