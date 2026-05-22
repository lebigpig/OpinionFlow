package com.lespider.opinionflow.spider.controller

import com.lespider.opinionflow.spider.service.StockCommentService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/comments")
class StockCommentController(
    private val stockCommentService: StockCommentService,
) {
    @GetMapping
    fun page(
        @RequestParam(name = "page", required = false, defaultValue = "0") page: Int,
        @RequestParam(name = "size", required = false, defaultValue = "50") size: Int,
        @RequestParam(name = "start", required = false) start: String?,
        @RequestParam(name = "end", required = false) end: String?,
        @RequestParam(name = "q", required = false) q: String?,
    ) = stockCommentService.pageComments(page, size, start, end, q)

    @GetMapping("/{id:\\d+}")
    fun getById(@PathVariable id: Long) = stockCommentService.getCommentById(id)
}