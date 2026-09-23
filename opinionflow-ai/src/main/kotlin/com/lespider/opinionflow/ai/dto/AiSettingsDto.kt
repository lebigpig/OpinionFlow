package com.lespider.opinionflow.ai.dto

/**
 * 一家 AI 厂商（OpenAI 兼容协议）的预置信息，供前端「AI 设置」弹窗的厂商下拉使用。
 *
 * @param id 厂商标识（请求头 X-AI-Provider 回传的取值）
 * @param label 展示名
 * @param baseUrl 预置接口地址（带 /v1 等路径；custom 厂商为空，由用户自行填写）
 * @param custom 是否为「自定义 OpenAI 兼容地址」项
 * @param note 界面提示文案
 * @param fallbackModels 远端 /models 拉取失败时的兜底模型清单
 */
data class AiProviderPresetDto(
    val id: String,
    val label: String,
    val baseUrl: String,
    val custom: Boolean = false,
    val note: String? = null,
    val fallbackModels: List<String> = emptyList(),
)

/**
 * 服务端默认 AI 配置（key.properties / application.yml）。
 * 出于安全考虑，api-key 仅回传「脱敏 + 前后各留 4 位」的形式，绝不回传明文。
 */
data class AiServerDefaultDto(
    val configured: Boolean,
    val provider: String? = null,
    val baseUrl: String = "",
    val model: String = "",
    val apiKeyMasked: String = "",
)

/**
 * GET /api/ai/settings 响应：当前生效配置 + 服务端默认配置 + 厂商预置清单。
 *
 * @param keySource 当前生效的 token 来源：request（前端设置）| server（后端默认）| none
 */
data class AiSettingsResponse(
    val provider: String? = null,
    val baseUrl: String = "",
    val model: String = "",
    val keySource: String = "none",
    val serverDefault: AiServerDefaultDto,
    val presets: List<AiProviderPresetDto>,
)

/** 单个可调用模型 */
data class AiModelDto(
    val id: String,
    val label: String? = null,
    val ownedBy: String? = null,
)

/**
 * GET /api/ai/models 响应：当前 token 下该 AI 服务可调用的模型清单。
 *
 * @param source remote（真实拉取 /models 成功）| fallback（远端不可用时返回预置兜底清单）
 * @param error 拉取失败原因（成功时为 null），前端据此提示「已展示兜底清单」
 */
data class AiModelsResponse(
    val provider: String? = null,
    val baseUrl: String = "",
    val keySource: String = "none",
    val source: String = "fallback",
    val models: List<AiModelDto>,
    val error: String? = null,
)
