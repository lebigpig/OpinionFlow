package com.lespider.opinionflow.ai.dto

data class ChatMemoryRequest(
    val sessionId: String? = null,
    val content: String? = null,
    val systemPrompt: String? = null,
    /** 选中的历史回答文件的完整内容，作为记忆上下文传入 */
    val selectedContent: String? = null,
    /** 是否启用联网搜索（Agent 自主调用 Tavily） */
    val webSearch: Boolean = false,
    /**
     * 是否启用通用 Agent 模式（webSearch=true 时绑定 Tavily 联网搜索工具）
     * @deprecated 建议使用 agentMode 替代
     */
    val agent: Boolean? = null,
    /**
     * Agent 模式：
     * - null / "general" / "": 传统对话（webSearch=true 时绑定 Tavily 联网搜索）
     * - "company-expert": 中国企业专家 Agent（绑定企业财报 + 新闻库 + Tavily + Tushare + 新浪财经 + AkShare 工具）
     */
    val agentMode: String? = null,
    /**
     * 外部 API Keys（如 Tushare token），仅在本次请求中生效，不持久化。
     * 例如 {"tushareToken": "xxxx"}；未传入时回退到配置文件。
     */
    val externalApiKeys: Map<String, String>? = null,
)
