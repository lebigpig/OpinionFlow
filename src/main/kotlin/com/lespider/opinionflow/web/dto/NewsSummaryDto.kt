package com.lespider.opinionflow.web.dto

import java.time.LocalDateTime

data class NewsSummaryDto(
    val id: Long,
    val title: String?,
    val publishTime: LocalDateTime?,
    val summary: String? = null,
)
