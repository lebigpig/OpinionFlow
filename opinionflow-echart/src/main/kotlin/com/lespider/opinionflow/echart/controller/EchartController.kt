package com.lespider.opinionflow.echart.controller

import com.lespider.opinionflow.echart.service.EchartService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/echart")
class EchartController(
    private val echartService: EchartService,
) {

    /**
     * 保存图表 JSON 数据
     * body: { "jsonText": "[{...}]" }
     */
    @PostMapping("/save")
    fun save(@RequestBody body: Map<String, String?>): Map<String, Any> {
        val jsonText = body["jsonText"]?.trim().orEmpty()
        if (jsonText.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "jsonText 不能为空")
        }

        return try {
            echartService.save(jsonText)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message, e)
        }
    }

    /**
     * 查询所有数据（原始列表）
     */
    @GetMapping("/list")
    fun list(): List<Map<String, Any>> {
        return echartService.listAll()
    }

    /**
     * 分页查询所有数据
     */
    @GetMapping("/page")
    fun page(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): com.lespider.opinionflow.echart.dto.PageDto<Map<String, Any>> {
        return echartService.page(page, size)
    }

    /**
     * 根据 ID 查询单条数据
     */
    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): Map<String, Any> {
        return echartService.getById(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "数据不存在: id=$id")
    }

    /**
     * 根据 ID 删除数据
     */
    @DeleteMapping("/{id}")
    fun deleteById(@PathVariable id: Long): Map<String, Any> {
        val deleted = echartService.deleteById(id)
        if (!deleted) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "数据不存在: id=$id")
        }
        return mapOf("deleted" to true, "id" to id)
    }
}