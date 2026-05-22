package com.lespider.opinionflow.news.dto

data class IdListResponse(
    val ids: List<Long>,
    val total: Long,
    val truncated: Boolean,
    val limit: Int,
)