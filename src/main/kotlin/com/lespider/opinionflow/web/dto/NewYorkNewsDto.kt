package com.lespider.opinionflow.web.dto

/**
 * 为了复用前端“雅虎新闻”列表样式，这里返回字段名保持一致：
 * - displayTime / articleUrl / imgUrl
 */
data class NewYorkNewsDto(
    val id: String,
    val title: String?,
    val summary: String?,
    val displayTime: String?,
    val articleUrl: String?,
    val imgUrl: String?,
)

