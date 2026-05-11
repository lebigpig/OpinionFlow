package com.lespider.opinionflow.web.dto

data class AiParseRequest(
    val content: String?,
    val systemPrompt: String? = null,
)
