package com.lespider.opinionflow.ai.service

import com.lespider.opinionflow.api.spider.SpiderScriptFeignClient
import com.lespider.opinionflow.api.spider.SpiderScriptRunRequest
import dev.langchain4j.agent.tool.Tool
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * AkShare 数据工具 — 供 LangChain4j Agent 调用。
 * AkShare 是免费 Python 库，无法从 Kotlin 直接调用，
 * 故通过 Feign 调用 opinionflow-spider 服务的 ScriptRunnerService 执行 Python 脚本（key=finance）。
 * 脚本在配置文件 opinionflow.scripts.finance-path 中指定。
 */
@Component
class AkShareTool(
    private val spiderScriptFeignClient: SpiderScriptFeignClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 通过 AkShare 查询股票/财经数据。
     * symbol 为股票代码（如 600519 或 600519.SH），mode 为查询类型：
     * info=个股信息, spot=实时行情, hist=历史行情, news=个股新闻, industry=行业数据。
     */
    @Tool("通过 AkShare 查询股票财经数据。当用户需要个股实时行情、个股信息、历史行情、个股相关新闻、行业数据而其他工具无法满足时使用。参数 symbol 为股票代码如 600519，mode 可选 info/spot/hist/news/industry，start 和 end 为日期（格式 YYYYMMDD，查询历史行情时使用）。")
    fun akshareQuery(symbol: String, mode: String, start: String? = null, end: String? = null): String {
        if (symbol.trim().isEmpty()) {
            return "股票代码不能为空。"
        }
        log.info("[Agent Tool] AkShare 查询: symbol={}, mode={}, start={}, end={}", symbol, mode, start, end)

        var params = StringBuilder("symbol=").append(symbol.trim())
        if (!mode.trim().isEmpty()) {
            params.append("&mode=").append(mode.trim())
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
                    symbol = symbol.trim(),
                    code = params.toString(),
                ),
            )
            if (response.ok) {
                // 脚本 stdout 通常为 JSON 或纯文本
                val out = response.stdout.trim()
                // 打印 AkShare 脚本（Python）实际输出，便于确认外部数据源返回内容
                log.info(
                    "[Agent Tool] AkShare 脚本执行成功: 耗时={}ms, stdout={} 字符, stderr={} 字符\n{}",
                    response.durationMs,
                    out.length,
                    response.stderr.length,
                    if (out.length <= 3000) out else out.take(3000) + "...(共 ${out.length} 字符，已截断)",
                )
                if (!out.isEmpty()) return out.take(8000)
                "AkShare 脚本执行成功但无输出（${response.durationMs}ms）"
            } else {
                log.warn(
                    "[Agent Tool] AkShare 脚本执行失败: message={}, stdout={}, stderr={}",
                    response.message,
                    response.stdout.take(500),
                    response.stderr.take(500),
                )
                "AkShare 脚本执行失败：${response.message ?: "未知原因"}${if (response.stderr.isEmpty()) "" else " | ${response.stderr.take(300)}"}"
            }
        } catch (e: Exception) {
            log.warn("[Agent Tool] AkShare 查询异常: {}", e.message)
            "AkShare 查询异常：${e.message}"
        }
    }
}