package com.lespider.opinionflow.echart.controller

import com.lespider.opinionflow.echart.service.MapMarkerService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/**
 * 地图标记 REST API（拖放图例后表单保存）
 * base: /api/map-markers
 */
@RestController
@RequestMapping("/api/map-markers")
class MapMarkerController(
    private val mapMarkerService: MapMarkerService,
) {

    /** 保存标记（POST body 为 JSON 对象） */
    @PostMapping
    fun save(@RequestBody body: Map<String, Any?>): Map<String, Any> {
        return try {
            mapMarkerService.save(body)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }
    }

    /** 查询所有标记 */
    @GetMapping
    fun list(): List<Map<String, Any>> = mapMarkerService.list()

    /** 根据 ID 查询单条 */
    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): Map<String, Any> {
        return mapMarkerService.getById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "标记不存在: id=$id")
    }

    /** 更新标记 */
    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody body: Map<String, Any?>): Map<String, Any> {
        val updated = try {
            mapMarkerService.update(id, body)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }
        return updated ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "标记不存在: id=$id")
    }

    /** 删除标记 */
    @DeleteMapping("/{id}")
    fun deleteById(@PathVariable id: Long): Map<String, Any> {
        if (!mapMarkerService.deleteById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "标记不存在: id=$id")
        }
        return mapOf("deleted" to true, "id" to id)
    }
}
