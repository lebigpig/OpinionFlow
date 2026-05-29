package com.lespider.opinionflow.ai.dto

data class ChatMemoryRequest(
    val sessionId: String? = null,
    val content: String? = null,
    val systemPrompt: String? = null,
    /** 选中的历史回答文件的完整内容，作为记忆上下文传入 */
    val selectedContent: String? = null,
    /** 是否启用联网搜索（Agent 自主调用 Tavily） */
    val webSearch: Boolean = false,
)
