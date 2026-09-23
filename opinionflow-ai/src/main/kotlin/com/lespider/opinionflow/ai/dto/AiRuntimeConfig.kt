package com.lespider.opinionflow.ai.dto

/**
 * 请求级 AI 运行时配置（前端「AI 设置」弹窗提交的覆盖项）。
 *
 * 优先级：**本次请求传入 > 服务端默认**（key.properties / application.yml 中的 opinionflow.ai.*）。
 * 所有字段均可为空，为空即表示「沿用服务端默认」。
 *
 * 由前端通过请求头传递（见 [AiRequestHeaders]），不落库、不写日志。
 */
data class AiRuntimeConfig(
    /** 厂商/协议标识（v1 仅 OpenAI 兼容：deepseek / openai / siliconflow / dashscope / moonshot / zhipu / openrouter / custom） */
    val provider: String? = null,
    /** OpenAI 兼容接口地址（支持 https://api.deepseek.com、.../v1、.../v1/chat/completions 等写法，由 AiUrlNormalizer 归一化） */
    val baseUrl: String? = null,
    /** 用户自己的 API Token（覆盖服务端默认 token，仅本次请求有效） */
    val apiKey: String? = null,
    /** 使用的模型名（覆盖服务端默认 model） */
    val model: String? = null,
) {
    /** 是否所有字段都为空（等价于「未携带任何覆盖配置」） */
    fun isBlank(): Boolean =
        provider.isNullOrBlank() && baseUrl.isNullOrBlank() && apiKey.isNullOrBlank() && model.isNullOrBlank()
}
