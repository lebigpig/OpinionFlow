package com.lespider.opinionflow.news.dto

import java.time.LocalDateTime

data class NewsSummaryDto(
    val id: Long,
    val title: String?,
    val time: LocalDateTime?,
    val content: String? = null,
)
