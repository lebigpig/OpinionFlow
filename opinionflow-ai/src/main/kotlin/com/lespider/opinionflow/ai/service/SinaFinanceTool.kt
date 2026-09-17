package com.lespider.opinionflow.ai.service

import dev.langchain4j.agent.tool.Tool
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.util.regex.Pattern
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 新浪财经实时数据工具 — 供 LangChain4j Agent 调用。
 * 免费、无需 token。通过新浪财经 hq.sinajs.cn 接口获取股票实时快照。
 * 请求需带 Referer: https://finance.sina.com.cn 且响应用 GBK/GB2312 编码。
 */
@Component
class SinaFinanceTool {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${opinionflow.finance.sina-base-url:https://hq.sinajs.cn/list=}")
    private var baseUrl: String = "https://hq.sinajs.cn/list="

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .build()

    /**
     * 查询新浪财经实时行情。
     * symbol 为股票代码列表，逗号分隔，需带交易所前缀：
     * sh=上海(如 sh600519 贵州茅台)，sz=深圳(如 sz000001 平安银行)，bj=北京证券交易所。
     */
    @Tool("查询新浪财经实时行情。当用户需要股票实时价格/涨跌幅/成交量等即时行情数据时调用。参数 symbol 为股票代码（需带交易所前缀，多个用逗号分隔），例如 sh600519,sz000001。返回实时行情字段。")
    fun sinaQuote(symbol: String): String {
        val sym = symbol.trim()
        if (sym.isEmpty()) {
            return "股票代码不能为空。示例：sh600519,sz000001"
        }
        log.info("[Agent Tool] 新浪行情查询: symbol={}", sym)

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + sym))
                .header("Referer", "https://finance.sina.com.cn")
                .header("User-Agent", "Mozilla/5.0")
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() != 200) {
                return "新浪财经请求失败：HTTP ${response.statusCode()}"
            }
            val text = String(response.body(), Charset.forName("GBK"))
            // 打印新浪 HTTP 原始响应（GBK 已解码）+ 解析后的行情，便于确认外部数据源输出
            log.info("[Agent Tool] 新浪 HTTP 原始响应({} 字符): {}", text.length, text.trim())
            val formatted = formatQuote(text)
            log.info("[Agent Tool] 新浪行情解析结果:\n{}", formatted)
            formatted
        } catch (e: Exception) {
            log.warn("[Agent Tool] 新浪财经查询异常: {}", e.message)
            "新浪财经查询异常：${e.message}"
        }
    }

    private fun formatQuote(raw: String): String {
        // 新浪返回格式：var hq_str_sh600519="名称,代码,当前价,昨日收盘,今日开盘,成交量,...,日期,...";
        val matcher = Pattern.compile("\\\"([^\\\"]*)\\\"").matcher(raw)
        val fields = if (matcher.find()) {
            matcher.group(1).split(",")
        } else {
            emptyList<String>()
        }

        if (fields.isEmpty() || fields.size < 5) {
            return "新浪财经未返回数据或数据不完整，请检查股票代码格式（如 sh600519）。原响应片段：${raw.take(200)}"
        }

        // 常见字段：0名称 1代码 2当前价 3昨日收盘 4今日开盘 5成交量(股) ... 21日期 22时间
        val name = fields[0]
        val price = fields[2]
        val prevClose = fields[3]
        val open = fields[4]
        val volume = fields[5]
        val day = if (fields.size > 21) fields[21] else ""
        val time = if (fields.size > 22) fields[22] else ""

        val change = try {
            val p = fields[2].toDouble()
            val pc = fields[3].toDouble()
            if (pc != 0.0) "${String.format("%.2f", p - pc)} (${String.format("%.2f", (p - pc) / pc * 100)}%)" else ""
        } catch (_: Exception) { "" }

        val code = if (fields.size > 1) fields[1] else ""

        return StringBuilder()
            .appendLine("【新浪实时行情】$name ($code)")
            .appendLine("当前价格：$price  昨日收盘：$prevClose  今日开盘：$open  涨跌：$change")
            .appendLine("成交量：$volume  交易时间：$day $time")
            .toString().trim()
    }
}