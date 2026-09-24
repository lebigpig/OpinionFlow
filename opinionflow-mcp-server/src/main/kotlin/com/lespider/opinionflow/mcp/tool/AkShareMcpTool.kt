package com.lespider.opinionflow.mcp.tool

import com.lespider.opinionflow.api.spider.SpiderScriptFeignClient
import com.lespider.opinionflow.api.spider.SpiderScriptRunRequest
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component

/**
 * AkShare 数据 MCP 工具。
 *
 * 从 opinionflow-ai 迁移而来（原 AkShareTool）。
 * AkShare 是免费 Python 库，无法从 Kotlin 直接调用，
 * 通过 OpenFeign 调用 opinionflow-spider 的 ScriptRunnerService 执行 Python 脚本（key=finance）。
 */
@Component
class AkShareMcpTool(
    private val spiderScriptFeignClient: SpiderScriptFeignClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 通过 AkShare 查询股票/财经数据。
     * @param symbol 股票代码（如 600519 或 600519.SH）
     * @param mode 查询类型：info / spot / hist / news / industry
     * @param start 开始日期（YYYYMMDD，查询历史行情时使用）
     * @param end 结束日期（YYYYMMDD，查询历史行情时使用）
     */
    @Tool(
        name = "akshareQuery",
        description = "通过 AkShare 查询股票财经数据。当用户需要个股实时行情、个股信息、历史行情、个股相关新闻、行业数据而其他工具无法满足时使用。参数 symbol 为股票代码如 600519，mode 可选 info/spot/hist/news/industry，start 和 end 为日期（格式 YYYYMMDD，查询历史行情时使用）。",
    )
    fun akshareQuery(symbol: String, mode: String, start: String? = null, end: String? = null): String {
        val sym = symbol.trim()
        if (sym.isEmpty()) {
            return "股票代码不能为空。"
        }
        val m = mode.trim()
        log.info("[MCP Tool] AkShare 查询: symbol={}, mode={}, start={}, end={}", sym, m, start, end)

        val params = StringBuilder("symbol=").append(sym)
        if (m.isNotEmpty()) {
            params.append("&mode=").append(m)
        }
        if (!start.isNullOrBlank()) {
            params.append("&start=").append(start.trim())
        }
        if (!end.isNullOrBlank()) {
            params.append("&end=").append(end.trim())
        }

        return try {
            val response = spiderScriptFeignClient.run(
                SpiderScriptRunRequest(
                    key = "finance",
                    symbol = sym,
                    code = params.toString(),
                ),
            )
            if (response.ok) {
                val out = response.stdout.trim()
                log.info(
                    "[MCP Tool] AkShare 脚本执行成功: 耗时={}ms, stdout={} 字符, stderr={} 字符\n{}",
                    response.durationMs,
                    out.length,
                    response.stderr.length,
                    if (out.length <= 3000) out else out.take(3000) + "...(共 ${out.length} 字符，已截断)",
                )
                if (out.isNotEmpty()) return out.take(8000)
                "AkShare 脚本执行成功但无输出（${response.durationMs}ms）"
            } else {
                log.warn(
                    "[MCP Tool] AkShare 脚本执行失败: message={}, stdout={}, stderr={}",
                    response.message,
                    response.stdout.take(500),
                    response.stderr.take(500),
                )
                "AkShare 脚本执行失败：${response.message ?: "未知原因"}${if (response.stderr.isEmpty()) "" else " | ${response.stderr.take(300)}"}"
            }
        } catch (e: Exception) {
            log.warn("[MCP Tool] AkShare 查询异常: {}", e.message)
            "AkShare 查询异常：${e.message}"
        }
    }
}
