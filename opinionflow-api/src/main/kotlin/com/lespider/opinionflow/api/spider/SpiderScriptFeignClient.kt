package com.lespider.opinionflow.api.spider

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * 爬虫/脚本服务 Feign 客户端
 * AI 服务（中国企业专家 Agent）通过此接口触发 spider 服务的 Python 脚本（含 AkShare 财务数据脚本）
 * 对应服务：opinionflow-spider（端口 9203，base /api/scripts）
 */
@FeignClient(name = "opinionflow-spider", path = "/api/scripts")
interface SpiderScriptFeignClient {

    @PostMapping("/run")
    fun run(@RequestBody request: SpiderScriptRunRequest): SpiderScriptRunResponse
}

/** 脚本运行请求：key=finance 时 symbol 为股票代码（如 600000 / 000001），code 为附加参数 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SpiderScriptRunRequest(
    val key: String? = null,
    val code: String? = null,
    val symbol: String? = null,
)

/** 脚本运行响应 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SpiderScriptRunResponse(
    val ok: Boolean = false,
    val key: String = "",
    val exitCode: Int? = null,
    val durationMs: Long = 0,
    val stdout: String = "",
    val stderr: String = "",
    val message: String? = null,
)