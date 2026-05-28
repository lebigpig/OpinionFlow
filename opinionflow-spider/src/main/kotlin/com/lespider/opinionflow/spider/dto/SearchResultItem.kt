package com.lespider.opinionflow.spider.dto

import java.math.BigDecimal

data class SearchResultItem(
    val query: String? = null,
    val url: String = "",
    val title: String? = null,
    val score: BigDecimal? = null,
    val publishedDate: String? = null,
    val content: String? = null,
    val rawContent: String? = null,
)
