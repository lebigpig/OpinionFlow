package com.lespider.opinionflow.ai.service

import com.lespider.opinionflow.ai.dto.AiRuntimeConfig
import org.springframework.stereotype.Component

/**
 * 请求级 AI 运行时配置管理器（前端「AI 设置」弹窗传入的 baseUrl / api-key / model 覆盖项）。
 *
 * 优先级：本次请求传入 > 服务端默认（key.properties / application.yml 的 opinionflow.ai.*）。
 *
 * 用 ThreadLocal 而非全局字段：AI 服务的「工具决策 → 工具执行 → 流式输出」全部在处理该请求的
 * 同一线程内同步完成（ChatMemoryService 内部以 future.get 阻塞），因此可精确隔离并发请求，
 * 避免 A 请求的自定义 token 被 B 请求使用（与 CompanyAgentKeyManager 的设计保持一致）。
 * 请求结束时由 ChatMemoryService 在 finally 中调用 clear() 清理，防止密钥残留。
 */
@Component
class AiRuntimeConfigManager {

    private val requestConfig = ThreadLocal<AiRuntimeConfig>()

    /** 注入本次请求的 AI 覆盖配置；为空则清除本线程存储（回退服务端默认） */
    fun inject(config: AiRuntimeConfig?) {
        if (config == null || config.isBlank()) {
            requestConfig.remove()
            return
        }
        requestConfig.set(config)
    }

    /** 当前请求的覆盖配置，未携带时为 null */
    fun current(): AiRuntimeConfig? = requestConfig.get()

    /** 请求结束清理，避免配置/密钥残留被后续请求读到 */
    fun clear() {
        requestConfig.remove()
    }
}
