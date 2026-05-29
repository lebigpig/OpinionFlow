package com.lespider.opinionflow.ai.service

import dev.langchain4j.agent.tool.Tool
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Tavily 联网搜索工具 — 供 LangChain4j Agent 调用。
 * AI 通过 Function Calling 自主决定何时搜索、搜索什么关键词。
 */
@Component
class TavilySearchTool(
    private val tavilyWebSearchService: TavilyWebSearchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Tool("搜索互联网获取最新信息。当你需要查找实时新闻、最新数据、市场行情、突发事件等无法从已有知识中获取的信息时，请调用此工具。参数 query 应为精炼的搜索关键词。")
    fun webSearch(query: String): String {
        log.info("[Agent Tool] AI 调用联网搜索: query='{}'", query)
        val result = tavilyWebSearchService.search(query)
        if (result.isEmpty()) {
            log.warn("[Agent Tool] 搜索无结果: query='{}'", query)
            return "未搜索到相关结果，请尝试使用不同的关键词。"
        }
        log.info("[Agent Tool] 搜索完成，返回 {} 字符", result.length)
        return result
    }
}