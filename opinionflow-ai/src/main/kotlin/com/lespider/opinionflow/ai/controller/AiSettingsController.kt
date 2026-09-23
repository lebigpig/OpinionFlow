package com.lespider.opinionflow.ai.controller

import com.lespider.opinionflow.ai.dto.AiModelsResponse
import com.lespider.opinionflow.ai.dto.AiRequestHeaders
import com.lespider.opinionflow.ai.dto.AiSettingsResponse
import com.lespider.opinionflow.ai.service.AiModelCatalogService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 「AI 设置」相关接口（前端 AppHeader 的设置按钮 → 设置弹窗）。
 *
 * 覆盖配置通过请求头传递（见 [AiRequestHeaders]），优先级：请求头 > 服务端默认配置。
 * 网关的 /api/ai 前缀路由已覆盖本控制器，无需修改 opinionflow-gateway。
 */
@RestController
@RequestMapping("/api/ai")
class AiSettingsController(
    private val modelCatalog: AiModelCatalogService,
) {
    /**
     * 当前 AI 配置概况：生效的 baseUrl / model / token 来源（request|server|none）、
     * 服务端默认配置（token 已脱敏）、厂商预置清单。前端打开设置弹窗时调用。
     */
    @GetMapping("/settings")
    fun settings(
        @RequestHeader(value = AiRequestHeaders.PROVIDER, required = false) provider: String?,
        @RequestHeader(value = AiRequestHeaders.BASE_URL, required = false) baseUrl: String?,
        @RequestHeader(value = AiRequestHeaders.API_KEY, required = false) apiKey: String?,
        @RequestHeader(value = AiRequestHeaders.MODEL, required = false) model: String?,
    ): AiSettingsResponse =
        modelCatalog.settings(AiRequestHeaders.toConfig(provider, baseUrl, apiKey, model))

    /**
     * 当前 token / 接口地址下可调用的模型清单（真实调用 {baseUrl}/models 获取）。
     * 远端不可用时返回预置兜底清单，source=fallback 且 error 说明原因。
     */
    @GetMapping("/models")
    fun models(
        @RequestHeader(value = AiRequestHeaders.PROVIDER, required = false) provider: String?,
        @RequestHeader(value = AiRequestHeaders.BASE_URL, required = false) baseUrl: String?,
        @RequestHeader(value = AiRequestHeaders.API_KEY, required = false) apiKey: String?,
        @RequestHeader(value = AiRequestHeaders.MODEL, required = false) model: String?,
    ): AiModelsResponse =
        modelCatalog.models(AiRequestHeaders.toConfig(provider, baseUrl, apiKey, model))
}
