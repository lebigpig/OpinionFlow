package com.lespider.opinionflow.ai.service

import com.lespider.opinionflow.ai.dto.AiProviderPresetDto
import org.springframework.stereotype.Component

/**
 * OpenAI 兼容厂商预置清单（前端「AI 设置」弹窗的厂商下拉数据源）。
 *
 * 说明：
 * - v1 只支持 **OpenAI 兼容协议** 一家（DeepSeek / OpenAI / 硅基流动 / 通义 / Kimi / 智谱 / OpenRouter / 自定义）；
 *   Claude、Gemini 的原生协议适配留待后续版本。
 * - baseUrl 一律写成「直接拼 /chat/completions 与 /models 都能通」的形式：
 *   带 /v1 等路径的按原样放行，不带路径的由 AiUrlNormalizer 自动补 /v1。
 * - fallbackModels 仅用于远端 /models 拉取失败时兜底，用户也可在界面手填模型名。
 */
@Component
class AiProviderPresets {

    val all: List<AiProviderPresetDto> = listOf(
        AiProviderPresetDto(
            id = "deepseek",
            label = "DeepSeek 深度求索",
            baseUrl = "https://api.deepseek.com",
            // 实测 DeepSeek GET /v1/models 返回 deepseek-flash / deepseek-v4-pro
            fallbackModels = listOf("deepseek-v4-pro", "deepseek-flash"),
        ),
        AiProviderPresetDto(
            id = "openai",
            label = "OpenAI 官方",
            baseUrl = "https://api.openai.com",
            fallbackModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini"),
        ),
        AiProviderPresetDto(
            id = "siliconflow",
            label = "硅基流动 SiliconFlow",
            baseUrl = "https://api.siliconflow.cn",
            fallbackModels = listOf("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct"),
        ),
        AiProviderPresetDto(
            id = "dashscope",
            label = "阿里云百炼（通义千问）",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            fallbackModels = listOf("qwen-plus", "qwen-turbo", "qwen-max"),
        ),
        AiProviderPresetDto(
            id = "moonshot",
            label = "月之暗面 Kimi",
            baseUrl = "https://api.moonshot.cn",
            fallbackModels = listOf("moonshot-v1-8k", "moonshot-v1-32k"),
        ),
        AiProviderPresetDto(
            id = "zhipu",
            label = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            fallbackModels = listOf("glm-4-plus", "glm-4-flash"),
        ),
        AiProviderPresetDto(
            id = "openrouter",
            label = "OpenRouter 聚合",
            baseUrl = "https://openrouter.ai/api/v1",
            fallbackModels = listOf("deepseek/deepseek-chat", "openai/gpt-4o-mini"),
        ),
        AiProviderPresetDto(
            id = "custom",
            label = "自定义 OpenAI 兼容地址",
            baseUrl = "",
            custom = true,
            note = "任意兼容 OpenAI 协议的服务或网关（如 one-api / new-api），填到 /v1 之前亦可，留空路径会自动补 /v1",
        ),
    )

    /** 按 id 查预置（大小写不敏感） */
    fun find(id: String?): AiProviderPresetDto? {
        val pid = id?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return all.firstOrNull { it.id.equals(pid, ignoreCase = true) }
    }

    /** 预置的兜底模型清单 */
    fun fallbackModels(id: String?): List<String> = find(id)?.fallbackModels.orEmpty()
}
