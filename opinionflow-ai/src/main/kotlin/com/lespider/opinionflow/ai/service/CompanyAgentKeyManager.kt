package com.lespider.opinionflow.ai.service

import org.springframework.stereotype.Component

/**
 * 中国企业专家 Agent 使用的外部 API Key 管理器。
 * 优先级：前端 chat 请求 externalApiKeys 传入 > 配置文件。
 * 由 ChatMemoryService 在 Agent 启动前将 keys 注入本管理器，
 * 各外部数据工具（Tushare 等）从本管理器读取。
 */
@Component
class CompanyAgentKeyManager {
    /**
     * 请求级外部 API Key 存储（ThreadLocal）。
     *
     * 用 ThreadLocal 而非全局 Map：AI 服务的工具决策 + 工具执行 + SSE 流式输出
     * 都在处理该请求的同一个线程上同步完成（ChatMemoryService 内部 future.get 阻塞），
     * 因此可精确隔离并发请求，避免 A 请求的 token 被 B 请求使用；
     * 请求结束时由 ChatMemoryService 调用 clear() 清理。
     */
    private val requestKeys = ThreadLocal<Map<String, String>>()

    /** 注入本次请求的外部 keys（key 统一小写；无有效 key 时清除本线程的存储） */
    fun inject(requestApiKeys: Map<String, String>?) {
        if (requestApiKeys.isNullOrEmpty()) {
            requestKeys.remove()
            return
        }
        requestKeys.set(
            requestApiKeys.entries
                .mapNotNull { (name, value) ->
                    val v = value?.trim().orEmpty()
                    if (v.isEmpty()) null else name.trim().lowercase() to v
                }
                .toMap(),
        )
    }

    /** 获取 key，未注入返回空 */
    fun get(name: String): String {
        return requestKeys.get()?.get(name.trim().lowercase()).orEmpty()
    }

    fun has(name: String): Boolean = get(name).isNotEmpty()

    /** 请求结束清理，避免密钥残留被后续请求读到 */
    fun clear() {
        requestKeys.remove()
    }
}