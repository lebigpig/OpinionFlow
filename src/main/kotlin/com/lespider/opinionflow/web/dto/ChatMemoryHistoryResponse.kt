package com.lespider.opinionflow.web.dto

data class ChatMemoryHistoryResponse(
    val sessionId: String,
    val messages: List<ChatMemoryMessageDto>,
)

data class ChatMemoryMessageDto(
    val role: String,
    val content: String,
)