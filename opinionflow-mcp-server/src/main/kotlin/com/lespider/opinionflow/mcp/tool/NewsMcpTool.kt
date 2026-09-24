package com.lespider.opinionflow.mcp.tool

import com.fasterxml.jackson.databind.JsonNode
import com.lespider.opinionflow.api.news.NewsFeignClient
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

/**
 * 新闻库检索 MCP 工具（数据来源：opinionflow-news 服务）。
 *
 * 从 opinionflow-ai 迁移而来（原 NewsSearchTool）。
 * 复用 opinionflow-api 的 NewsFeignClient，通过 OpenFeign（Nacos + LoadBalancer）访问 opinionflow-news。
 */
@Component
class NewsMcpTool(
    private val newsFeignClient: NewsFeignClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 检索新闻库中的财经快讯。
     * @param keyword 关键词（如公司名、股票代码、行业）
     * @param start 开始日期（yyyy-MM-dd，可省略）
     * @param end 结束日期（yyyy-MM-dd，可省略）
     */
    @Tool(
        name = "searchFinanceNews",
        description = "检索项目新闻库中的财经快讯。当你需要查询已收录的财经新闻（如当日市场要闻、个股新闻、行业动态）时调用。参数 keyword 为关键词，start 和 end 为日期范围（格式 yyyy-MM-dd，可省略）。返回新闻标题与时间列表。",
    )
    fun searchFinanceNews(keyword: String, start: String? = null, end: String? = null): String {
        log.info("[MCP Tool] 检索财经快讯: q='{}', start={}, end={}", keyword, start, end)
        return try {
            formatNewsPage("财经快讯", newsFeignClient.finance(0, 20, start, end, keyword))
        } catch (e: Exception) {
            log.warn("[MCP Tool] 财经快讯检索失败: {}", e.message)
            "财经快讯检索失败：${e.message}"
        }
    }

    /**
     * 检索新闻库中的通用新闻（网易）。
     * @param keyword 关键词（如公司名、行业、宏观话题）
     * @param start 开始日期（yyyy-MM-dd，可省略）
     * @param end 结束日期（yyyy-MM-dd，可省略）
     */
    @Tool(
        name = "searchGeneralNews",
        description = "检索项目新闻库中的通用新闻（网易）。当你需要查询公司/行业/宏观相关的新闻报道时调用。参数 keyword 为关键词，start 和 end 为日期范围（格式 yyyy-MM-dd，可省略）。返回新闻标题与时间列表。",
    )
    fun searchGeneralNews(keyword: String, start: String? = null, end: String? = null): String {
        log.info("[MCP Tool] 检索通用新闻: q='{}', start={}, end={}", keyword, start, end)
        return try {
            formatNewsPage("通用新闻", newsFeignClient.general(0, 20, start, end, keyword))
        } catch (e: Exception) {
            log.warn("[MCP Tool] 通用新闻检索失败: {}", e.message)
            "通用新闻检索失败：${e.message}"
        }
    }

    /**
     * 获取财经快讯全文。
     * @param id 财经快讯 id（从 searchFinanceNews 返回中获取）
     */
    @Tool(
        name = "getFinanceNewsDetail",
        description = "获取财经快讯全文。参数 id 为财经快讯 id（从 searchFinanceNews 返回结果中获取）。返回该条快讯的完整内容。",
    )
    fun getFinanceNewsDetail(id: Long): String {
        log.info("[MCP Tool] 获取财经快讯全文: id={}", id)
        return try {
            formatNewsDetail(newsFeignClient.financeDetail(id))
        } catch (e: Exception) {
            log.warn("[MCP Tool] 财经快讯详情获取失败: {}", e.message)
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
