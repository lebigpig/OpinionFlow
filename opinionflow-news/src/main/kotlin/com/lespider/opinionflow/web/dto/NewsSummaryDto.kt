package com.lespider.opinionflow.web.dto

import java.time.LocalDateTime

data class NewsSummaryDto(
    val id: Long,
    val title: String?,
    val time: LocalDateTime?,
    val summary: String? = null,
    val content: String? = null,
)
