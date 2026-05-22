package com.lespider.opinionflow.api.rag

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * RAG 向量检索服务 Feign 客户端
 * AI 服务通过此接口调用 RAG 服务进行向量检索
 */
@FeignClient(name = "opinionflow-rag", path = "/internal/rag")
interface RagFeignClient {

    @PostMapping("/search")
    fun search(@RequestBody request: RagSearchRequest): List<RagSearchResult>

    @PostMapping("/status")
    fun status(): RagStatusResponse
}

data class RagSearchRequest(
    val query: String,
    val topK: Int = 5,
    val maxDistance: Double = 50.0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RagSearchResult(
    val source: String = "",
    val title: String = "",
    val content: String = "",
    val publishTime: String = "",
    val distance: Double = 0.0,
)

data class RagStatusResponse(
    val enabled: Boolean,
    val collectionName: String,
    val imported: Boolean,
)