package com.lespider.opinionflow.ai.dto

data class ChatMemoryRequest(
    val sessionId: String? = null,
    val content: String? = null,
    val systemPrompt: String? = null,
    /** 选中的历史回答文件的完整内容，作为记忆上下文传入 */
    val selectedContent: String? = null,
)