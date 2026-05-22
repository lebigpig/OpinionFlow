package com.lespider.opinionflow.rag.controller

import com.lespider.opinionflow.rag.service.MilvusNewsImportService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

/**
 * RAG 向量检索服务控制器
 * 提供 REST API 供 AI 服务通过 Feign 远程调用
 */
@RestController
@RequestMapping("/internal/rag")
class RagController(
    private val milvusNewsImportService: MilvusNewsImportService?
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 向量检索接口
     */
    @PostMapping("/search")
    fun search(@RequestBody request: RagSearchRequest): List<RagSearchResult> {
        val service = milvusNewsImportService
        if (service == null) {
            log.warn("[RAG] Milvus 服务未启用（milvus.enabled=false），返回空结果")
            return emptyList()
        }
        val results = service.search(request.query, request.topK ?: 5)
        return results.map { doc ->
            RagSearchResult(
                source = doc.source,
                title = doc.title,
                content = doc.content,
                publishTime = doc.publishTime,
                distance = doc.score,
            )
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    fun health(): Map<String, String> {
        val milvusStatus = if (milvusNewsImportService != null) "enabled" else "disabled"
        return mapOf("status" to "UP", "service" to "opinionflow-rag", "milvus" to milvusStatus)
    }
}

/**
 * RAG 检索请求
 */
data class RagSearchRequest(
    val query: String,
    val topK: Int? = 5
)

/**
 * RAG 检索结果 - 字段需与 opinionflow-api 中的 RagSearchResult 保持一致
 */
data class RagSearchResult(
    val source: String = "",
    val title: String = "",
    val content: String = "",
    val publishTime: String = "",
    val distance: Double = 0.0,
)
