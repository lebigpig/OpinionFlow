package com.lespider.opinionflow.web.dto

data class AiAnswerListResponse(
    val files: List<AiAnswerFileInfo>,
)

data class AiAnswerFileInfo(
    val filename: String,
    val savedPath: String,
    val timestamp: String,
)
