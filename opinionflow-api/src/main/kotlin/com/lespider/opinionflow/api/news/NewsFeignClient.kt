package com.lespider.opinionflow.api.news

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

/**
 * 新闻服务 Feign 客户端
 * AI 服务（中国企业专家 Agent）通过此接口调用 opinionflow-news 服务查询新闻库
 * 对应服务：opinionflow-news（端口 9201，base /api/news）
 */
@FeignClient(name = "opinionflow-news", path = "/api/news")
interface NewsFeignClient {

    /** 通用新闻分页（按关键词/时间过滤） */
    @GetMapping
    fun general(
        @RequestParam("page") page: Int,
        @RequestParam("size") size: Int,
        @RequestParam("start") start: String?,
        @RequestParam("end") end: String?,
        @RequestParam("q") q: String?,
    ): JsonNode

    /** 财经快讯分页（falsh_news 表，按关键词/时间过滤） */
    @GetMapping("/finance")
    fun finance(
        @RequestParam("page") page: Int,
        @RequestParam("size") size: Int,
        @RequestParam("start") start: String?,
        @RequestParam("end") end: String?,
        @RequestParam("q") q: String?,
    ): JsonNode

    /** 通用新闻详情 */
    @GetMapping("/{id}")
    fun detail(@PathVariable("id") id: Long): JsonNode

    /** 财经快讯详情 */
    @GetMapping("/finance/{id}")
    fun financeDetail(@PathVariable("id") id: Long): JsonNode
}