package com.lespider.opinionflow.mcp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.cloud.openfeign.EnableFeignClients

/**
 * MCP Server 独立微服务。
 *
 * 提供一组 MCP 工具（Spring AI @Tool 注解），通过 SSE 传输暴露给外部 MCP Client。
 * opinionflow-ai 服务通过 MCP Client（Nacos 服务发现 + McpSyncClient）远程调用这些工具。
 *
 * 第一批迁移工具：Tavily 联网搜索 / 项目新闻库检索 / AkShare 财经数据。
 */
@SpringBootApplication(scanBasePackages = ["com.lespider.opinionflow.common", "com.lespider.opinionflow.mcp"])
@EnableDiscoveryClient
@EnableFeignClients(basePackages = ["com.lespider.opinionflow.api"])
class McpServerApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<McpServerApplication>(*args)
        }
    }
}
