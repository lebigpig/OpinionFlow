package com.lespider.opinionflow.news.controller

import com.lespider.opinionflow.news.service.NewsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/news")
class NewsController(
    private val newsService: NewsService,
) {
    @GetMapping("/deepseek-menu")
    fun deepseekMenu(
        @RequestParam(name = "page", required = false, defaultValue = "0") page: Int,
        @RequestParam(name = "size", required = false, defaultValue = "50") size: Int,
        @RequestParam(name = "start", required = false) start: String?,
        @RequestParam(name = "end", required = false) end: String?,
        @RequestParam(name = "q", required = false) q: String?,
    ) = newsService.pageDeepseekMenu(page, size, start, end, q)

    @GetMapping
    fun general(
        @RequestParam(name = "page", required = false, defaultValue = "0") page: Int,
        @RequestParam(name = "size", required = false, defaultValue = "50") size: Int,
        @RequestParam(name = "start", required = false) start: String?,
        @RequestParam(name = "end", required = false) end: String?,
        @RequestParam(name = "q", required = false) q: String?,
    ) = newsService.pageGeneral(page, size, start, end, q)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long) = newsService.getById(id)

    @GetMapping("/finance")
    fun finance(
        @RequestParam(name = "page", required = false, defaultValue = "0") page: Int,
        @RequestParam(name = "size", required = false, defaultValue = "50") size: Int,
        @RequestParam(name = "start", required = false) start: String?,
        @RequestParam(name = "end", required = false) end: String?,
        @RequestParam(name = "q", required = false) q: String?,
    ) = newsService.pageFinance(page, size, start, end, q)

    @GetMapping("/finance/ids")
    fun financeIds(
        @RequestParam(name = "start", required = false) start: String?,
        @RequestParam(name = "end", required = false) end: String?,
        @RequestParam(name = "q", required = false) q: String?,
        @RequestParam(name = "limit", required = false) limit: Int?,
    ) = newsService.financeIds(start, end, q, limit)

    @GetMapping("/finance/{id}")
    fun financeDetail(@PathVariable id: Long) = newsService.getFinanceById(id)
}