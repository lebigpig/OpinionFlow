package com.lespider.opinionflow.news.dto

import java.time.LocalDateTime

data class NewsDetailDto(
    val id: Long,
    val title: String?,
    val content: String?,
    val time: LocalDateTime?,
)
