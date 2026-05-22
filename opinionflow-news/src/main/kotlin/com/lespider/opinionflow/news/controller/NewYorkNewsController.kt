package com.lespider.opinionflow.news.controller

import com.lespider.opinionflow.news.service.NewYorkNewsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/nytimes")
class NewYorkNewsController(
    private val newYorkNewsService: NewYorkNewsService,
) {
    @GetMapping
    fun page(
        @RequestParam(name = "page", required = false, defaultValue = "0") page: Int,
        @RequestParam(name = "size", required = false, defaultValue = "50") size: Int,
        @RequestParam(name = "start", required = false) start: String?,
        @RequestParam(name = "end", required = false) end: String?,
        @RequestParam(name = "q", required = false) q: String?,
    ) = newYorkNewsService.page(page, size, start, end, q)
}