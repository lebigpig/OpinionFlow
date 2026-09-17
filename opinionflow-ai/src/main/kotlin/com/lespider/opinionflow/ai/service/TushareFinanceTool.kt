package com.lespider.opinionflow.ai.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.agent.tool.Tool
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Tushare 外部财经数据工具 — 供 LangChain4j Agent 调用。
 * Tushare token 优先级：前端 chat 请求 externalApiKeys.tushareToken > 配置文件 opinionflow.finance.tushare-token
 * 说明：token 不会暴露给 AI，仅由本工具内部使用。
 */
@Component
class TushareFinanceTool(
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${opinionflow.finance.tushare-token:}")
    private var configuredToken: String = ""

    @Value("\${opinionflow.finance.tushare-base-url:https://api.tushare.pro}")
    private var baseUrl: String = "https://api.tushare.pro"

    /**
     * 当前请求的 token（ThreadLocal）。
     * 由 ChatMemoryService 在 Agent 启动前注入前端传入的 externalApiKeys，
     * 请求结束由 ChatMemoryService 调用 clearRuntimeToken() 清理，
     * 避免残留 token 被后续无 token 的请求复用。
     */
    private val runtimeToken = ThreadLocal<String>()

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .build()

    /** 注入前端传来的 Tushare token（无则回退配置） */
    fun setRuntimeToken(token: String?) {
        val t = token?.trim()
        if (t.isNullOrEmpty()) runtimeToken.remove() else runtimeToken.set(t)
    }

    /** 请求结束清理请求级 token */
    fun clearRuntimeToken() {
        runtimeToken.remove()
    }

    /**
     * 查询 Tushare 外部财经数据。
     * apiName 为 Tushare 接口名（如 stock_zh_a_hist、stock_zh_a_daily、stk_annual_report 等），
     * params 为 JSON 字符串（如 {"symbol":"600519.SH","start":"20240101","end":"20241231"}）。
     */
    @Tool("查询 Tushare 外部财经数据。当用户需要公司财务数据、股票历史行情、股票列表、行业/宏观数据等 Tushare 提供的专业数据且项目财报库缺少时使用。参数 apiName 为 Tushare 接口名（如 stock_zh_a_hist、stock_zh_a_daily、stk_annual_report、stock_zh_a_spot），params 为 JSON 字符串如 {\"symbol\":\"600519.SH\",\"start\":\"20240101\",\"end\":\"20241231\"}。")
    fun tushareQuery(apiName: String, params: String): String {
        val token = runtimeToken.get() ?: configuredToken
        if (token.isBlank()) {
            return "Tushare token 未配置。请在前端请求中提供 externalApiKeys.tushareToken，或在配置文件设置 opinionflow.finance.tushare-token。"
        }
        log.info("[Agent Tool] Tushare 查询: api={}, params={}", apiName, params)

        val requestBody = objectMapper.writeValueAsString(mapOf(
            "api_name" to apiName,
            "token" to token,
            "params" to parseParams(params),
            "fields" to "",
        ))

        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(java.time.Duration.ofSeconds(30))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.warn("[Agent Tool] Tushare HTTP 失败: status={}, body={}", response.statusCode(), response.body().take(300))
                return "Tushare 请求失败：HTTP ${response.statusCode()}, ${response.body().take(300)}"
            }
            // 打印 Tushare HTTP 原始响应体，便于确认"外部数据源到底返回了什么"
            val rawBody = response.body()
            val rawForLog = if (rawBody.length <= 2000) rawBody else rawBody.take(2000) + "...(共 ${rawBody.length} 字符，已截断)"
            log.info("[Agent Tool] Tushare HTTP 响应体({} 字符): {}", rawBody.length, rawForLog)
            formatResponse(json = objectMapper.readTree(rawBody))
        } catch (e: Exception) {
            log.warn("[Agent Tool] Tushare 查询异常: {}", e.message)
            "Tushare 查询异常：${e.message}"
        }
    }

    private fun parseParams(raw: String): Map<String, Any?> {
        if (raw.isNullOrBlank()) return mapOf()
        return try {
            val node = objectMapper.readTree(raw)
            if (node == null || !node.isObject) return mapOf<String, Any?>("symbol" to raw)
            val result = linkedMapOf<String, Any?>()
            node.fields().forEachRemaining { (k, v) ->
                if (!v.isNull) {
                    result[k] = if (v.isValueNode) v.asText() else v.toString()
                }
            }
            result
        } catch (_: Exception) {
            mapOf<String, Any?>("symbol" to raw)
        }
    }

    private fun formatResponse(json: JsonNode): String {
        val code = json.get("code")?.asInt() ?: -1
        if (code != 0) {
            val msg = json.get("msg")?.asText() ?: json.get("message")?.asText() ?: "未知错误"
            return "Tushare 查询失败：$msg"
        }
        val data = json.get("data") ?: return "Tushare 无返回数据"
        val fields = data.get("fields")
        val items = data.get("items")

        if (fields == null || items == null) {
            return "Tushare 返回格式不符：${data.toString().take(800)}"
        }

        val fieldNames = mutableListOf<String>()
        for (f in fields) fieldNames.add(f.asText())

        val sb = StringBuilder()
        sb.appendLine("【Tushare 数据表】列：${fieldNames.joinToString(", ")}")
        var count = 0
        for (row in items) {
            if (count >= 50) {
                sb.appendLine("...（共 ${items.size()} 行，仅显示前 50 行）")
                break
            }
            val cells = mutableListOf<String>()
            for (i in 0.until(row.size())) {
                val cell = row.get(i)
                cells.add(if (cell == null || cell.isNull) "null" else cell.asText())
            }
            sb.appendLine("${count + 1}. ${cells.joinToString(" | ")}")
            count++
        }
        return sb.toString().trim()
    }
}