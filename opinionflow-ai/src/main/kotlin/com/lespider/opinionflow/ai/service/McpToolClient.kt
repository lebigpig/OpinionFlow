package com.lespider.opinionflow.ai.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport
import io.modelcontextprotocol.spec.McpSchema
import jakarta.annotation.PreDestroy
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.stereotype.Component

/**
 * MCP Client 桥接器（opinionflow-ai 侧）。
 *
 * 作用：把「前端 → AI（LangChain4j Agent）」中以 LangChain4j [ToolSpecification] +
 * 手动 [ToolExecutionRequest] 模型运行的工具调用，桥接到远程的 `opinionflow-mcp-server`
 * （通过 Nacos 服务发现 + MCP 官方 SDK 的 SSE 传输）。
 *
 * 首批迁移工具：Tavily/webSearch、News(searchFinanceNews/searchGeneralNews/getFinanceNewsDetail)、
 * AkShare(akshareQuery)。其余财经工具保持在本地 LangChain4j @Tool。
 *
 * 设计要点：
 *  - Nacos DiscoveryClient 按服务名解析 opinionflow-mcp-server 的 host:port => SSE 地址。
 *  - 建立单个 McpSyncClient（懒连接，首次取工具/调用时初始化）。
 *  - [toolSpecs] 把 MCP 的 listTools() 定义的工具转换为 LangChain4j ToolSpecification，
 *    供现有手动工具循环的「工具决策」阶段使用。
 *  - [executeTool] 把 LangChain4j 的 ToolExecutionRequest 转发给 MCP callTool 执行。
 *  - MCP 服务器不可用时优雅降级：返回空工具表，executeTool 返回错误提示，
 *    保证 AI 服务正常启动并降级为纯文本对话。
 */
@Component
class McpToolClient(
    private val discoveryClient: DiscoveryClient,
    private val objectMapper: ObjectMapper,
    @Value("\${opinionflow.mcp.server-name:opinionflow-mcp-server}") private val serverName: String,
    @Value("\${opinionflow.mcp.sse-path:/sse}") private val ssePath: String,
    @Value("\${opinionflow.mcp.timeout-seconds:30}") private val timeoutSeconds: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var syncClient: McpSyncClient? = null

    @Volatile
    private var clientUri: String? = null

    private val toolNames = ConcurrentHashMap<String, Boolean>()

    /** 由 opinionflow-mcp-server 暴露的 MCP 工具对应的 LangChain4j 工具规格（可为空列表）。 */
    fun toolSpecs(): List<ToolSpecification> {
        val client = connect()
            ?: run {
                log.warn("[MCP] 无法连接 opinionflow-mcp-server，返回空工具集")
                return emptyList()
            }
        return try {
            val tools = client.listTools().tools()
            log.info("[MCP] 从 '{}' 拉取到 {} 个工具: {}", clientUri, tools?.size ?: 0,
                tools?.joinToString(", ") { it.name() })
            tools?.map { tool ->
                toolNames[tool.name()] = true
                toToolSpecification(tool)
            } ?: emptyList()
        } catch (e: Exception) {
            log.error("[MCP] listTools 失败: {}", e.message, e)
            emptyList()
        }
    }
/**
     * 转发 LangChain4j 工具调用到 MCP。
     * @param toolName 工具名（对应 MCP 工具名）
     * @param argumentsJson LangChain4j 传来的 arguments JSON 字符串
     * @return MCP callTool 返回的文本
     */
    fun executeTool(toolName: String, argumentsJson: String): String {
        if (toolNames.isEmpty() && toolNames.containsKey(toolName).not()) {
            log.warn("[MCP] 工具 '{}' 不在已知 MCP 工具表中，直接透传尝试调用", toolName)
        }
        val client = connect()
            ?: return "MCP 服务器(opinionflow-mcp-server)不可达，无法调用工具 '$toolName'。"
        return try {
            val arguments = parseArguments(argumentsJson)
            log.info(
                "[MCP] callTool: name='{}', args='{}'",
                toolName,
                if (argumentsJson.length <= 500) argumentsJson else argumentsJson.take(500) + "...(长度 ${argumentsJson.length})",
            )
            val startedAt = System.currentTimeMillis()
            val result = client.callTool(McpSchema.CallToolRequest(toolName, arguments))
            val text = extractText(result)
            log.info(
                "[MCP] callTool 返回: name='{}', 耗时={}ms, isError={}, 长度={} 字符\n--- MCP 输出开始 ---\n{}\n--- MCP 输出结束 ---",
                toolName,
                System.currentTimeMillis() - startedAt,
                result.isError(),
                text.length,
                if (text.length <= 3000) text else text.take(3000) + "...(长度 ${text.length})",
            )
            text
        } catch (e: Exception) {
            val msg = "MCP 工具 '$toolName' 调用异常: ${e.message}"
            log.error(msg, e)
            msg
        }
    }

    private fun toToolSpecification(tool: McpSchema.Tool): ToolSpecification {
        val builder = ToolSpecification.builder()
            .name(tool.name())
            .description(tool.description() ?: tool.name())
        val schema = tool.inputSchema()
        if (schema != null) {
            builder.parameters(toJsonObjectSchema(schema))
        }
        return builder.build()
    }

    private fun toJsonObjectSchema(schema: McpSchema.JsonSchema): JsonObjectSchema {
        val jsonBuilder = JsonObjectSchema.builder()
        val properties: Map<String, Any>? = schema.properties()
        if (properties != null) {
            for ((name, raw) in properties) {
                val node: JsonNode = try {
                    objectMapper.valueToTree(raw)
                } catch (_: Exception) {
                    null
                } ?: objectMapper.createObjectNode()
                val type = node.get("type")?.asText()
                val desc = node.get("description")?.asText() ?: ""
                when (type) {
                    "integer" -> jsonBuilder.addIntegerProperty(name, desc)
                    "number" -> jsonBuilder.addNumberProperty(name, desc)
                    "boolean" -> jsonBuilder.addBooleanProperty(name, desc)
                    else -> jsonBuilder.addStringProperty(name, desc)
                }
            }
        }
        val required: List<String>? = schema.required()
        if (required != null && required.isNotEmpty()) {
            jsonBuilder.required(*required.toTypedArray())
        }
        return jsonBuilder.build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseArguments(argumentsJson: String): Map<String, Any> {
        if (argumentsJson.isBlank()) return emptyMap()
        return try {
            objectMapper.readValue(argumentsJson, Map::class.java) as? Map<String, Any> ?: emptyMap()
        } catch (e: Exception) {
            log.warn("[MCP] arguments 解析失败，按原始字符串处理: {}", e.message)
            emptyMap()
        }
    }

    private fun extractText(result: McpSchema.CallToolResult): String {
        val sb = StringBuilder()
        val content: List<McpSchema.Content> = result.content() ?: emptyList()
        for (c in content) {
            when (c) {
                is McpSchema.TextContent -> sb.appendLine(c.text())
                else -> sb.appendLine(c.toString())
            }
        }
        if (sb.isBlank() && result.isError() == true) {
            sb.append("(MCP 调用返回错误且无文本内容)")
        }
        return sb.toString().trim()
    }
/** 懒初始化：解析 Nacos 实例地址并建立 SSE 客户端连接。 */
    private fun connect(): McpSyncClient? {
        syncClient?.let { return it }

        var result: McpSyncClient? = null
        synchronized(this) {
            syncClient?.let { result = it }
            if (result == null) {
                result = buildSyncClient()
                if (result != null) {
                    this.syncClient = result
                }
            }
        }
        return result
    }

    private fun buildSyncClient(): McpSyncClient? {
        val uri = resolveServerUri()
        if (uri == null) {
            log.warn("[MCP] 无法解析 opinionflow-mcp-server 地址，跳过连接")
            return null
        }
        clientUri = uri
        return try {
            val transport = HttpClientSseClientTransport
                .Builder(uri)
                .sseEndpoint(ssePath)
                .build()

            val client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(timeoutSeconds))
                .initializationTimeout(Duration.ofSeconds(timeoutSeconds))
                .capabilities(McpSchema.ClientCapabilities(null, null, null))
                .build()

            client.initialize()
            log.info("[MCP] 已初始化到 opinionflow-mcp-server @ {}", uri)
            client
        } catch (e: Exception) {
            log.error("[MCP] 连接 opinionflow-mcp-server 失败: {}", e.message)
            null
        }
    }

    /** 通过 Nacos 服务发现解析 opinionflow-mcp-server 的地址。 */
    private fun resolveServerUri(): String? {
        try {
            val instances = discoveryClient.getInstances(serverName)
            if (instances.isNullOrEmpty()) {
                log.warn("[MCP] Nacos 未发现服务 '{}' 的实例", serverName)
                return null
            }
            val instance = instances.first()
            val host = instance.host
            val port = instance.port
            val scheme = if (port == 8443 || instance.metadata?.get("secure") == "true") "https" else "http"
            val uri = "$scheme://$host:$port"
            log.info("[MCP] Nacos 解析 '{}' -> {} (共 {} 个实例)", serverName, uri, instances.size)
            return uri
        } catch (e: Exception) {
            log.error("[MCP] Nacos 服务发现解析失败: {}", e.message)
            return null
        }
    }

    @PreDestroy
    fun shutdown() {
        try {
            syncClient?.close()
        } catch (_: Exception) {
        }
        syncClient = null
    }
}