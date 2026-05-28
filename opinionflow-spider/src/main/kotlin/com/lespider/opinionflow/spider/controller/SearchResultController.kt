package com.lespider.opinionflow.spider.controller

import com.lespider.opinionflow.spider.dto.SearchResultItem
import com.lespider.opinionflow.spider.service.SearchResultService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/search-results")
class SearchResultController(
    private val searchResultService: SearchResultService,
) {
    @PostMapping("/save")
    fun saveResults(@RequestBody items: List<SearchResultItem>) =
        searchResultService.saveResults(items)
}