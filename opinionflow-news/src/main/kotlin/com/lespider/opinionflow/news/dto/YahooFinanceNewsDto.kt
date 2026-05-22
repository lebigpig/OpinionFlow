package com.lespider.opinionflow.news.dto

data class YahooFinanceNewsDto(
    val id: String,
    val title: String?,
    val summary: String?,
    val displayTime: String?,
    val articleUrl: String?,
    val imgUrl: String?,
)