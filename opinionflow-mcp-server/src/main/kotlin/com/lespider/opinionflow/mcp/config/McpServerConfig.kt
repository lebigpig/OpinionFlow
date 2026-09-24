package com.lespider.opinionflow.mcp.config

import com.lespider.opinionflow.mcp.tool.AkShareMcpTool
import com.lespider.opinionflow.mcp.tool.NewsMcpTool
import com.lespider.opinionflow.mcp.tool.TavilyMcpTool
import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * MCP Server 工具注册配置。
 *
 * 将带 Spring AI @Tool 注解的工具组件（Tavily / News / AkShare）包装为
 * [ToolCallbackProvider]，供 Spring AI 的 McpServerAutoConfiguration 自动收集
 * 并注册为 MCP 工具（通过 SSE 传输暴露给 MCP Client）。
 */
@Configuration
class McpServerConfig {

    @Bean
    fun mcpToolCallbackProvider(
        tavilyMcpTool: TavilyMcpTool,
        newsMcpTool: NewsMcpTool,
        akShareMcpTool: AkShareMcpTool,
    ): ToolCallbackProvider =
        MethodToolCallbackProvider.builder()
            .toolObjects(tavilyMcpTool, newsMcpTool, akShareMcpTool)
            .build()
}
