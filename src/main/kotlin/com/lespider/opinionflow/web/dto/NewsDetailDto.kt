package com.lespider.opinionflow.web.dto

import java.time.LocalDateTime

data class NewsDetailDto(
    val id: Long,
    val title: String?,
    val content: String?,
    val publishTime: LocalDateTime?,
)
