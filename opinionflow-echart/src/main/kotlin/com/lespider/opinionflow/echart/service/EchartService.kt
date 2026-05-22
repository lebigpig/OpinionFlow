package com.lespider.opinionflow.echart.service

import com.lespider.opinionflow.echart.domain.EchartData
import com.lespider.opinionflow.echart.dto.PageDto
import com.lespider.opinionflow.echart.repo.EchartDataRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Echart 图表数据服务（数据库存储版本）
 * content 字段存储完整的 JSON 报告数据
 */
@Service
class EchartService(
    private val repository: EchartDataRepository,
    private val objectMapper: ObjectMapper,
) {

    /**
     * 保存图表 JSON 数据到数据库
     * 直接将完整 JSON 存入 content 字段
     */
    @Transactional
    fun save(jsonText: String): Map<String, Any> {
        val entity = EchartData(content = jsonText)
        val saved = repository.save(entity)
        return mapOf(
            "id" to (saved.id ?: 0L),
            "createdAt" to saved.createdAt.toString(),
        )
    }

    /**
     * 查询所有数据（原始列表）
     * 返回每条记录的 id、解析后的 content、创建时间
     */
    fun listAll(): List<Map<String, Any>> {
        return repository.findAllByOrderByCreatedAtDesc().map { toMap(it) }
    }

    /**
     * 分页查询所有数据
     */
    fun page(page: Int, size: Int): PageDto<Map<String, Any>> {
        val p = page.coerceAtLeast(0)
        val s = size.coerceIn(1, 200)
        val result = repository.findAll(PageRequest.of(p, s))
        return PageDto(
            content = result.content.map { toMap(it) },
            page = p,
            size = s,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    /**
     * 根据 ID 查询单条数据
     */
    fun getById(id: Long): Map<String, Any>? {
        return repository.findById(id).map { toMap(it) }.orElse(null)
    }

    /**
     * 根据 ID 删除数据
     */
    @Transactional
    fun deleteById(id: Long): Boolean {
        return if (repository.existsById(id)) {
            repository.deleteById(id)
            true
        } else {
            false
        }
    }

    // ========== 私有方法 ==========

    /**
     * 实体转 Map
     * content 字段尝试解析为 JSON 对象/数组，解析失败则作为字符串返回
     */
    private fun toMap(entity: EchartData): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to (entity.id ?: 0L),
            "createdAt" to entity.createdAt.toString(),
        )
        // 尝试将 content 解析为 JSON
        try {
            val parsed = objectMapper.readValue(entity.content, Any::class.java)
            map["content"] = parsed
        } catch (e: Exception) {
            map["content"] = entity.content
        }
        return map
    }
}