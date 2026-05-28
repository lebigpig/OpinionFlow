package com.lespider.opinionflow.spider.controller

import com.lespider.opinionflow.spider.dto.TavilySearchRequest
import com.lespider.opinionflow.spider.dto.TavilySearchResponse
import com.lespider.opinionflow.spider.service.TavilySearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tavily")
class TavilySearchController(
    private val tavilySearchService: TavilySearchService,
) {

    @PostMapping("/search")
    fun search(@RequestBody req: TavilySearchRequest): ResponseEntity<TavilySearchResponse> {
        if (req.query.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val result = tavilySearchService.search(req)
        return ResponseEntity.ok(result)
    }
}