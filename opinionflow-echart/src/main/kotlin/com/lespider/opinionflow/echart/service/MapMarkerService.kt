package com.lespider.opinionflow.echart.service

import com.lespider.opinionflow.echart.domain.MapMarker
import com.lespider.opinionflow.echart.repo.MapMarkerRepository
import java.math.BigDecimal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 地图标记服务：拖放图例后表单保存 / 查询 / 更新 / 删除
 */
@Service
class MapMarkerService(
    private val repository: MapMarkerRepository,
) {

    @Transactional
    fun save(body: Map<String, Any?>): Map<String, Any> {
        val latitude = toBigDecimal(body["latitude"])
            ?: throw IllegalArgumentException("latitude 不能为空")
        val longitude = toBigDecimal(body["longitude"])
            ?: throw IllegalArgumentException("longitude 不能为空")
        val name = str(body["name"])
        if (name.isBlank()) throw IllegalArgumentException("name 不能为空")

        val entity = MapMarker(
            latitude = latitude,
            longitude = longitude,
            name = name,
            category = str(body["category"]),
            iconType = str(body["iconType"]).ifBlank { str(body["icon_type"]) },
            country = strOrNull(body["country"]),
            region = strOrNull(body["region"]),
            description = strOrNull(body["description"]),
            annualOutput = strOrNull(body["annualOutput"]) ?: strOrNull(body["annual_output"]),
            annualProfit = strOrNull(body["annualProfit"]) ?: strOrNull(body["annual_profit"]),
            operator = strOrNull(body["operator"]),
            status = (body["status"] as? Number)?.toInt() ?: 1,
            createdBy = strOrNull(body["createdBy"]) ?: strOrNull(body["created_by"]),
        )
        return toMap(repository.save(entity))
    }

    @Transactional
    fun update(id: Long, body: Map<String, Any?>): Map<String, Any>? {
        val existing = repository.findById(id).orElse(null) ?: return null
        val entity = MapMarker(
            id = existing.id,
            latitude = toBigDecimal(body["latitude"]) ?: existing.latitude,
            longitude = toBigDecimal(body["longitude"]) ?: existing.longitude,
            name = str(body["name"]).ifBlank { existing.name },
            category = str(body["category"]).ifBlank { existing.category },
            iconType = str(body["iconType"]).ifBlank { existing.iconType },
            country = strOrNull(body["country"]) ?: existing.country,
            region = strOrNull(body["region"]) ?: existing.region,
            description = strOrNull(body["description"]) ?: existing.description,
            annualOutput = strOrNull(body["annualOutput"]) ?: existing.annualOutput,
            annualProfit = strOrNull(body["annualProfit"]) ?: existing.annualProfit,
            operator = strOrNull(body["operator"]) ?: existing.operator,
            status = (body["status"] as? Number)?.toInt() ?: existing.status,
            updatedAt = java.time.LocalDateTime.now(),
            createdBy = strOrNull(body["createdBy"]) ?: existing.createdBy,
        )
        return toMap(repository.save(entity))
    }

    fun list(): List<Map<String, Any>> = repository.findAllByOrderByCreatedAtDesc().map { toMap(it) }

    fun getById(id: Long): Map<String, Any>? = repository.findById(id).map { toMap(it) }.orElse(null)

    @Transactional
    fun deleteById(id: Long): Boolean {
        if (!repository.existsById(id)) return false
        repository.deleteById(id)
        return true
    }

    // ========== 私有工具 ==========

    private fun toMap(e: MapMarker): Map<String, Any> = linkedMapOf(
        "id" to (e.id ?: 0L),
        "latitude" to e.latitude.toPlainString(),
        "longitude" to e.longitude.toPlainString(),
        "name" to e.name,
        "category" to e.category,
        "iconType" to e.iconType,
        "country" to (e.country ?: ""),
        "region" to (e.region ?: ""),
        "description" to (e.description ?: ""),
        "annualOutput" to (e.annualOutput ?: ""),
        "annualProfit" to (e.annualProfit ?: ""),
        "operator" to (e.operator ?: ""),
        "status" to e.status,
        "createdAt" to e.createdAt.toString(),
        "updatedAt" to e.updatedAt.toString(),
        "createdBy" to (e.createdBy ?: ""),
    )

    private fun str(v: Any?): String = v?.toString()?.trim().orEmpty()

    private fun strOrNull(v: Any?): String? = v?.toString()?.trim()?.takeIf { it.isNotEmpty() }

    private fun toBigDecimal(v: Any?): BigDecimal? = when (v) {
        is BigDecimal -> v
        is Number -> BigDecimal(v.toString())
        is String -> v.trim().toBigDecimalOrNull()
        else -> null
    }
}
