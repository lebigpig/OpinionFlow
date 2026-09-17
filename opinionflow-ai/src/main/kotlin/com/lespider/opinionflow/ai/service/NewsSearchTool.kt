package com.lespider.opinionflow.ai.service

import com.fasterxml.jackson.databind.JsonNode
import com.lespider.opinionflow.api.news.NewsFeignClient
import dev.langchain4j.agent.tool.Tool
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 新闻库检索工具 — 供 LangChain4j Agent 调用。
 * 数据来源：opinionflow-news 服务（网易财经快讯 / 网易通用新闻）
 * AI 通过 Function Calling 自主决定是否检索新闻库（与 Tavily 联网搜索互补：
 * 新闻库是本项目已采集入库的历史新闻，联网搜索是实时外部信息）。
 */
@Component
class NewsSearchTool(
    private val newsFeignClient: NewsFeignClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 检索新闻库中的财经快讯。
     * q 为关键词（如公司名、股票代码、行业），start/end 为起止时间（yyyy-MM-dd）。
     */
    @Tool("检索项目新闻库中的财经快讯。当你需要查询已收录的财经新闻（如当日市场要闻、个股新闻、行业动态）时调用。参数 q 为关键词，start 和 end 为日期范围（格式 yyyy-MM-dd，可省略）。返回新闻标题与时间列表。")
    fun searchFinanceNews(keyword: String, start: String? = null, end: String? = null): String {
        log.info("[Agent Tool] 检索财经快讯: q='{}', start={}, end={}", keyword, start, end)
        return try {
            val page = newsFeignClient.finance(0, 20, start, end, keyword)
            formatNewsPage("财经快讯", page)
        } catch (e: Exception) {
            log.warn("[Agent Tool] 财经快讯检索失败: {}", e.message)
            "财经快讯检索失败：${e.message}"
        }
    }

    /**
     * 检索新闻库中的通用新闻（网易）。
     * q 为关键词（如公司名、行业、宏观话题）。
     */
    @Tool("检索项目新闻库中的通用新闻（网易）。当你需要查询公司/行业/宏观相关的新闻报道时调用。参数 q 为关键词，start 和 end 为日期范围（格式 yyyy-MM-dd，可省略）。返回新闻标题与时间列表。")
    fun searchGeneralNews(keyword: String, start: String? = null, end: String? = null): String {
        log.info("[Agent Tool] 检索通用新闻: q='{}', start={}, end={}", keyword, start, end)
        return try {
            val page = newsFeignClient.general(0, 20, start, end, keyword)
            formatNewsPage("通用新闻", page)
        } catch (e: Exception) {
            log.warn("[Agent Tool] 通用新闻检索失败: {}", e.message)
            "通用新闻检索失败：${e.message}"
        }
    }

    /**
     * 根据新闻 id 获取财经快讯全文。
     * id 从 searchFinanceNews 返回的列表中获取。
     */
    @Tool("获取财经快讯全文。参数 id 为财经快讯 id（从 searchFinanceNews 返回结果中获取）。返回该条快讯的完整内容。")
    fun getFinanceNewsDetail(id: Long): String {
        log.info("[Agent Tool] 获取财经快讯全文: id={}", id)
        return try {
            formatNewsDetail(newsFeignClient.financeDetail(id))
        } catch (e: Exception) {
            log.warn("[Agent Tool] 财经快讯详情获取失败: {}", e.message)
            "财经快讯详情获取失败：${e.message}"
        }
    }

    private fun formatNewsPage(title: String, node: JsonNode): String {
        val total = node.get("totalElements")?.asLong() ?: 0
        if (total == 0L) {
            return "$title：未找到匹配的新闻，请尝试其他关键词。"
        }
        val sb = StringBuilder()
        sb.appendLine("$title（共 $total 条，显示前 20 条）：")
        val content = node.get("content")
        if (content != null && content.isArray) {
            for (item in content) {
                val id = item.get("id")?.asLong() ?: -1
                val t = item.get("title")?.asText() ?: item.get("time")?.asText() ?: ""
                val time = item.get("time")?.asText() ?: ""
                sb.appendLine("- id=$id | $time | $t")
            }
        }
        return sb.toString().trim()
    }

    private fun formatNewsDetail(node: JsonNode): String {
        val title = node.get("title")?.asText() ?: ""
        val time = node.get("time")?.asText() ?: ""
        val content = node.get("content")?.asText() ?: ""
        return """
            【标题】$title
            【时间】$time
            【内容】${content.take(2000)}
        """.trimIndent()
    }
}