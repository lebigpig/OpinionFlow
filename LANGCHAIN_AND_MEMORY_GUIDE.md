# OpinionFlow 集成 LangChain4j + 记忆化实现方案

## 一、为什么选择 LangChain4j 而非 Python LangChain

OpinionFlow 后端是 **Spring Boot + Kotlin** 项目，因此推荐使用 **LangChain4j**（Java/Kotlin 原生版本），而非 Python 的 LangChain。

| 对比 | Python LangChain | LangChain4j |
|------|-----------------|-------------|
| 语言 | Python | Java / Kotlin |
| Spring Boot 集成 | 需要桥接 | **原生支持 `spring-boot-starter`** |
| 与现有代码兼容 | 需重写 | **渐进式集成**，可保留现有 AiParseService |
| 流式支持 | ✅ | ✅ |
| 记忆/对话历史 | ✅ | ✅ (`ChatMemory`) |
| 向量存储 | ✅ | ✅（Redis/Elasticsearch/内存） |

---

## 二、引入 LangChain4j 依赖

### build.gradle.kts

```kotlin
dependencies {
    // LangChain4j 核心
    implementation("dev.langchain4j:langchain4j:0.35.0")
    
    // LangChain4j Spring Boot Starter（自动配置）
    implementation("dev.langchain4j:langchain4j-spring-boot-starter:0.35.0")
    
    // DeepSeek 支持（OpenAI 兼容）
    implementation("dev.langchain4j:langchain4j-open-ai:0.35.0")
    
    // 记忆存储 - 用 Redis
    implementation("dev.langchain4j:langchain4j-redis:0.35.0")
    
    // 可选：向量存储（用于语义搜索新闻）
    implementation("dev.langchain4j:langchain4j-embeddings-bge-small-zh-v15:0.35.0")
}
```

### application.properties 新增配置

```properties
# LangChain4j DeepSeek 配置
langchain4j.open-ai.chat-model.api-key=${AI_API_KEY}
langchain4j.open-ai.chat-model.base-url=https://api.deepseek.com
langchain4j.open-ai.chat-model.model-name=deepseek-v4-flash
langchain4j.open-ai.chat-model.temperature=0.7
langchain4j.open-ai.chat-model.log-requests=true
langchain4j.open-ai.chat-model.log-responses=true
```

---

## 三、记忆化（Chat Memory）实现方案

### 3.1 什么是记忆化

**记忆化** = 让 AI "记住" 之前发送过的新闻标题和内容，实现上下文关联分析。

例如：
```
第1轮：发送新闻 "美联储宣布降息50基点" → AI 分析：利好...
第2轮：发送新闻 "A股大涨3%" → AI 回忆第1轮的降息新闻，分析：受降息利好影响...
```

### 3.2 记忆类型选择

| 记忆类型 | 说明 | 适用场景 |
|----------|------|----------|
| **消息窗口记忆** | 保留最近 N 轮对话 | ✅ 推荐：新闻分析（节省 token） |
| **Token 窗口记忆** | 保留最近 N 个 token 的对话 | 适合长对话 |
| **摘要记忆** | 将旧对话压缩为摘要 | 超长对话场景 |
| **持久化记忆** | 存入 Redis/DB，跨会话保留 | 多用户、需历史回溯 |

### 3.3 方案 A：内存消息窗口记忆（最简单）

```kotlin
package com.lespider.opinionflow.service

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import org.springframework.stereotype.Service

/**
 * 带记忆的 AI 舆情分析助手
 */
interface NewsAnalyst {
    @SystemMessage("""
        你是舆情与新闻分析助手。
        请结合用户之前发送的新闻，进行综合关联分析。
        输出：要点摘要、情绪/立场倾向、关键词、多条新闻的关联分析、可跟进建议。
    """)
    fun analyze(@MemoryId sessionId: String, @UserMessage message: String): String
}

@Service
class AiMemoryService(
    private val chatModel: ChatLanguageModel,
) {
    // 每个会话保留最近 20 轮对话（约 40 条消息）
    private val chatMemories = mutableMapOf<String, ChatMemory>()

    private fun getChatMemory(sessionId: String): ChatMemory {
        return chatMemories.getOrPut(sessionId) {
            MessageWindowChatMemory.withMaxMessages(20)
        }
    }

    /**
     * 带记忆的分析（自动关联之前发送的新闻）
     */
    fun analyzeWithMemory(sessionId: String, content: String): String {
        val analyst = AiServices.builder(NewsAnalyst::class.java)
            .chatLanguageModel(chatModel)
            .chatMemory(getChatMemory(sessionId))
            .build()

        return analyst.analyze(sessionId, content)
    }

    /**
     * 查看某个会话的记忆历史
     */
    fun getMemoryHistory(sessionId: String): List<String> {
        val memory = chatMemories[sessionId] ?: return emptyList()
        return memory.messages().map { "${it.type()}: ${it.text()}" }
    }

    /**
     * 清除某个会话的记忆
     */
    fun clearMemory(sessionId: String) {
        chatMemories.remove(sessionId)
    }
}
```

**使用示例：**

```kotlin
// 第1次：发送降息新闻
val result1 = aiMemoryService.analyzeWithMemory(
    sessionId = "user-123",
    content = "【新闻标题】美联储宣布降息50基点\n【正文】美联储今日宣布降息50个基点..."
)
// AI 返回：降息将刺激经济...

// 第2次：发送 A 股新闻（AI 会自动关联之前的降息新闻）
val result2 = aiMemoryService.analyzeWithMemory(
    sessionId = "user-123", 
    content = "【新闻标题】A股三大指数大涨3%\n【正文】受美联储降息消息影响..."
)
// AI 返回：如您之前关注的降息消息，本次 A 股大涨与其密切相关...
```

### 3.4 方案 B：Redis 持久化记忆（跨会话 + 多设备）

```kotlin
package com.lespider.opinionflow.service

import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Redis 持久化聊天记忆
 * - 跨服务器重启保留
 * - 支持多设备同步
 */
@Service
class RedisChatMemoryService(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val prefix = "opinionflow:chat:memory:"
    private val maxMessages = 20

    /**
     * 获取或创建聊天记忆提供者
     */
    fun chatMemoryProvider(): ChatMemoryProvider {
        return ChatMemoryProvider { sessionId ->
            val memory = MessageWindowChatMemory.withMaxMessages(maxMessages)

            // 从 Redis 加载历史消息
            val messages = loadMessages(sessionId)
            messages.forEach { memory.add(it) }

            // 包装：每次 add 时自动同步到 Redis
            object : ChatMemory by memory {
                override fun add(message: ChatMessage) {
                    memory.add(message)
                    saveMessages(sessionId, memory.messages())
                }
            }
        }
    }

    private fun loadMessages(sessionId: String): List<ChatMessage> {
        val key = "$prefix$sessionId"
        val json = redisTemplate.opsForValue().get(key) ?: return "[]"
        return try {
            objectMapper.readValue(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveMessages(sessionId: String, messages: List<ChatMessage>) {
        val key = "$prefix$sessionId"
        val json = objectMapper.writeValueAsString(messages)
        redisTemplate.opsForValue().set(key, json)
        // 设置过期时间 24 小时
        redisTemplate.expire(key, java.time.Duration.ofHours(24))
    }

    fun clearMemory(sessionId: String) {
        redisTemplate.delete("$prefix$sessionId")
    }
}
```

### 3.5 方案 C：摘要记忆（处理大量新闻时压缩历史）

```kotlin
package com.lespider.opinionflow.service

import dev.langchain4j.memory.chat.ChatMemory
import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import org.springframework.stereotype.Service

/**
 * 带摘要压缩的记忆提供者
 * 
 * 策略：
 * - 保留最近 5 轮对话原文
 * - 超过 5 轮的旧对话压缩为一段摘要
 * - 下次对话时，摘要作为 SystemMessage 注入
 */
@Service
class SummarizingChatMemoryService(
    private val chatModel: ChatLanguageModel,
) {
    private val memories = mutableMapOf<String, MutableList<ChatMessage>>()
    private val summaries = mutableMapOf<String, String>()

    fun getMemory(sessionId: String): ChatMemory {
        val memory = MessageWindowChatMemory.withMaxMessages(50) // 大窗口
        summaries[sessionId]?.let { summary ->
            memory.add(SystemMessage.from("【历史摘要】$summary"))
        }
        memories[sessionId]?.forEach { memory.add(it) }
        return memory
    }

    /**
     * 当消息过多时，压缩旧消息为摘要
     */
    fun maybeSummarize(sessionId: String, threshold: Int = 20) {
        val messages = memories.getOrPut(sessionId) { mutableListOf() }
        if (messages.size <= threshold) return

        // 取出前半部分进行摘要
        val toSummarize = messages.take(messages.size / 2)
        val remaining = messages.drop(messages.size / 2)
        memories[sessionId] = remaining.toMutableList()

        // 调用 AI 生成摘要
        val content = toSummarize.joinToString("\n") { "${it.type()}: ${it.text()}" }
        val prompt = """
            请将以下新闻分析对话历史压缩为简洁摘要（保留关键信息）：
            $content
        """.trimIndent()

        val summary = chatModel.generate(listOf(UserMessage.from(prompt)))
        val newSummary = summaries[sessionId]?.let { 
            "$it\n${summary.text()}" 
        } ?: summary.text()
        
        summaries[sessionId] = newSummary
    }
}
```

---

## 四、完整的带记忆 AI 分析服务（推荐实现）

```kotlin
package com.lespider.opinionflow.service

import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.service.*
import dev.langchain4j.service.spring AiService
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// ============ AI 服务接口 ============

interface NewsAnalysisAssistant {
    @SystemMessage("""
        你是专业的舆情与新闻分析助手。
        
        请记住用户之前发送的所有新闻，并在分析新新闻时：
        1. 单独分析每条新闻的要点
        2. 与之前发送的新闻进行关联分析
        3. 识别多条新闻之间的因果关系、矛盾关系或共振关系
        4. 给出综合舆情判断和投资建议
        
        分析格式：
        📰 单条新闻分析
        🔗 关联分析（与之前新闻的关系）
        📊 综合舆情判断
        💡 可跟进建议
    """)
    fun analyze(@MemoryId sessionId: String, @UserMessage message: String): String
}

// ============ 带记忆的 AI 服务实现 ============

@Service
class AiMemoryService(
    private val chatModel: ChatLanguageModel,
) {
    // 会话记忆存储（生产环境应持久化到 Redis）
    private val chatMemories = ConcurrentHashMap<String, ChatMemory>()
    
    // 会话新闻历史（用于前端展示）
    private val newsHistory = ConcurrentHashMap<String, MutableList<String>>()

    companion object {
        private const val MAX_MESSAGES = 30  // 保留最近30轮对话
        private const val MAX_NEWS_HISTORY = 50  // 保留最近50条新闻
    }

    /**
     * 获取或创建 AI 分析助手（带记忆）
     */
    private fun getAssistant(sessionId: String): NewsAnalysisAssistant {
        return AiServices.builder(NewsAnalysisAssistant::class.java)
            .chatLanguageModel(chatModel)
            .chatMemory(getOrCreateMemory(sessionId))
            .build()
    }

    /**
     * 获取或创建聊天记忆
     */
    private fun getOrCreateMemory(sessionId: String): ChatMemory {
        return chatMemories.getOrPut(sessionId) {
            MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES)
        }
    }

    /**
     * 🌟 核心方法：带记忆的新闻分析
     * AI 会自动记住之前所有发送过的新闻
     */
    fun analyzeWithMemory(
        sessionId: String,
        newsTitle: String,
        newsContent: String,
        customPrompt: String? = null
    ): String {
        val assistant = getAssistant(sessionId)
        
        // 构建消息（带自定义 prompt 支持）
        val message = buildString {
            appendLine("【新闻标题】$newsTitle")
            appendLine("【新闻正文】$newsContent")
            if (!customPrompt.isNullOrBlank()) {
                appendLine("\n【用户额外要求】$customPrompt")
            }
            // 提示 AI 参考历史
            val history = newsHistory.getOrDefault(sessionId, mutableListOf())
            if (history.isNotEmpty()) {
                appendLine("\n【提醒】你之前已经分析过 ${history.size} 条新闻，"
                    + "请结合之前的分析结果进行关联分析。")
            }
        }

        val result = assistant.analyze(sessionId, message)

        // 记录到新闻历史
        val history = newsHistory.getOrPut(sessionId) { mutableListOf() }
        history.add("📰 $newsTitle | 分析结果长度: ${result.length} 字")
        if (history.size > MAX_NEWS_HISTORY) {
            history.removeFirst()
        }

        return result
    }

    /**
     * 流式带记忆分析（SSE）
     */
    fun analyzeWithMemoryStream(
        sessionId: String,
        newsTitle: String,
        newsContent: String,
        onDelta: (String) -> Unit
    ) {
        val memory = getOrCreateMemory(sessionId)
        
        val message = "【新闻标题】$newsTitle\n【新闻正文】$newsContent"
        
        // 添加用户消息到记忆
        memory.add(UserMessage.from(message))
        
        // 流式调用
        chatModel.chatStreaming()
            .chatMessages(memory.messages())
            .onPartialResponse { delta -> onDelta(delta) }
            .onCompleteResponse { response: ChatResponse ->
                // 将 AI 回复添加到记忆
                memory.add(response.aiMessage())
            }
            .build()
            .chat()

        // 记录历史
        val history = newsHistory.getOrPut(sessionId) { mutableListOf() }
        history.add("📰 $newsTitle")
    }

    /**
     * 获取某会话的新闻分析历史
     */
    fun getNewsHistory(sessionId: String): List<String> {
        return newsHistory.getOrDefault(sessionId, emptyList()).toList()
    }

    /**
     * 获取某会话的完整对话记忆
     */
    fun getMemoryHistory(sessionId: String): List<Map<String, String>> {
        val memory = chatMemories[sessionId] ?: return emptyList()
        return memory.messages().map { msg ->
            mapOf(
                "type" to msg.type().toString(),
                "content" to msg.text()
            )
        }
    }

    /**
     * 清除会话记忆
     */
    fun clearSession(sessionId: String) {
        chatMemories.remove(sessionId)
        newsHistory.remove(sessionId)
    }

    /**
     * 生成新的会话 ID
     */
    fun createSessionId(): String {
        return UUID.randomUUID().toString()
    }
}
```

---

## 五、Controller 集成

```kotlin
package com.lespider.opinionflow.web

import com.lespider.opinionflow.service.AiMemoryService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors

@RestController
@RequestMapping("/api/ai-memory")
class AiMemoryController(
    private val aiMemoryService: AiMemoryService,
) {
    /**
     * 带记忆的同步分析
     * POST /api/ai-memory/analyze
     */
    @PostMapping("/analyze")
    fun analyze(@RequestBody body: Map<String, String>): Map<String, Any> {
        val sessionId = body["sessionId"] ?: aiMemoryService.createSessionId()
        val title = body["title"] ?: ""
        val content = body["content"] ?: ""
        val customPrompt = body["customPrompt"]

        val result = aiMemoryService.analyzeWithMemory(sessionId, title, content, customPrompt)

        return mapOf(
            "sessionId" to sessionId,
            "analysis" to result,
            "historyCount" to aiMemoryService.getNewsHistory(sessionId).size
        )
    }

    /**
     * 带记忆的流式分析 (SSE)
     * POST /api/ai-memory/analyze/stream
     */
    @PostMapping("/analyze/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun analyzeStream(@RequestBody body: Map<String, String>): SseEmitter {
        val sessionId = body["sessionId"] ?: aiMemoryService.createSessionId()
        val title = body["title"] ?: ""
        val content = body["content"] ?: ""

        val emitter = SseEmitter(0L)
        val executor = Executors.newSingleThreadExecutor()

        // 先发送 sessionId（前端后续请求携带）
        emitter.send(SseEmitter.event().name("sessionId").data(sessionId))

        executor.submit {
            try {
                aiMemoryService.analyzeWithMemoryStream(sessionId, title, content) { delta ->
                    emitter.send(SseEmitter.event().name("delta").data(delta))
                }
                emitter.send(SseEmitter.event().name("historyCount")
                    .data(aiMemoryService.getNewsHistory(sessionId).size))
                emitter.complete()
            } catch (e: Exception) {
                emitter.completeWithError(e)
            } finally {
                executor.shutdown()
            }
        }

        return emitter
    }

    /**
     * 查看会话的新闻分析历史
     */
    @GetMapping("/history/{sessionId}")
    fun history(@PathVariable sessionId: String): Map<String, Any> {
        return mapOf(
            "sessionId" to sessionId,
            "newsHistory" to aiMemoryService.getNewsHistory(sessionId),
            "memoryMessages" to aiMemoryService.getMemoryHistory(sessionId)
        )
    }

    /**
     * 清除会话记忆
     */
    @DeleteMapping("/session/{sessionId}")
    fun clearSession(@PathVariable sessionId: String): Map<String, String> {
        aiMemoryService.clearSession(sessionId)
        return mapOf("status" to "cleared", "sessionId" to sessionId)
    }
}
```

---

## 六、前端集成示例

```javascript
// src/lib/aiMemoryApi.js

let currentSessionId = null;

/**
 * 带记忆的新闻分析
 */
export async function analyzeWithMemory(title, content, customPrompt = null) {
  const response = await fetch('/api/ai-memory/analyze', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      sessionId: currentSessionId,
      title,
      content,
      customPrompt
    })
  });
  
  const data = await response.json();
  currentSessionId = data.sessionId;  // 保存 sessionId，后续请求复用
  return data;
}

/**
 * 带记忆的流式分析
 */
export function analyzeWithMemoryStream(title, content, onDelta) {
  return new Promise((resolve, reject) => {
    fetch('/api/ai-memory/analyze/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sessionId: currentSessionId,
        title,
        content
      })
    }).then(response => {
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      
      function read() {
        reader.read().then(({ done, value }) => {
          if (done) return resolve();
          const text = decoder.decode(value);
          // 解析 SSE 事件
          const lines = text.split('\n');
          for (const line of lines) {
            if (line.startsWith('data:')) {
              const data = line.slice(5).trim();
              if (data.startsWith('{')) {
                const parsed = JSON.parse(data);
                if (parsed.sessionId) currentSessionId = parsed.sessionId;
                else if (parsed.analysis) onDelta(parsed.analysis);
              } else {
                onDelta(data);
              }
            }
          }
          read();
        });
      }
      read();
    }).catch(reject);
  });
}

/**
 * 清除当前会话记忆
 */
export async function clearMemory() {
  if (!currentSessionId) return;
  await fetch(`/api/ai-memory/session/${currentSessionId}`, {
    method: 'DELETE'
  });
  currentSessionId = null;
}

/**
 * 查看历史
 */
export async function getHistory() {
  if (!currentSessionId) return null;
  const resp = await fetch(`/api/ai-memory/history/${currentSessionId}`);
  return resp.json();
}
```

---

## 七、记忆化的数据流图

```
┌──────────────────────────────────────────────────────────┐
│                     前端 (Vue 3)                           │
│  ┌──────────────┐    ┌──────────────┐                    │
│  │ 新闻列表页    │    │ AI 分析页     │                    │
│  │ 点击"分析"   │───→│ 发送到后端    │                    │
│  └──────────────┘    └──────┬───────┘                    │
│                              │ 携带 sessionId             │
└──────────────────────────────┼───────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────┐
│                 POST /api/ai-memory/analyze               │
│  ┌─────────────────────────────────────────────────┐     │
│  │  AiMemoryController                              │     │
│  │     │                                            │     │
│  │     ▼                                            │     │
│  │  AiMemoryService                                 │     │
│  │     │                                            │     │
│  │     ├── 1. 获取 ChatMemory(sessionId)            │     │
│  │     │      ├── 有历史 → 加载之前的新闻对话        │     │
│  │     │      └── 无历史 → 创建新记忆窗口           │     │
│  │     │                                            │     │
│  │     ├── 2. 构建消息                               │     │
│  │     │      "【新闻标题】xxx"                       │     │
│  │     │      "【新闻正文】xxx"                       │     │
│  │     │      + "你之前分析过 N 条新闻"              │     │
│  │     │                                            │     │
│  │     ├── 3. 注入记忆 → LangChain4j ChatMemory     │     │
│  │     │      [SystemMessage: 角色设定]              │     │
│  │     │      [UserMessage: 第1条新闻] ← 记忆       │     │
│  │     │      [AiMessage: 第1条分析]   ← 记忆       │     │
│  │     │      [UserMessage: 第2条新闻] ← 记忆       │     │
│  │     │      [AiMessage: 第2条分析]   ← 记忆       │     │
│  │     │      ...                                    │     │
│  │     │      [UserMessage: 当前新闻]  ← 新输入      │     │
│  │     │                                            │     │
│  │     └── 4. 发送到 DeepSeek API                    │     │
│  │            ↓                                      │     │
│  │        AI 看到完整历史 → 输出关联分析              │     │
│  │            ↓                                      │     │
│  │        将 AI 回复存入记忆                          │     │
│  └─────────────────────────────────────────────────┘     │
│                               │                           │
│                               ▼                           │
│  ┌─────────────────────────────────────────────────┐     │
│  │  ChatMemory (内存 / Redis)                        │     │
│  │                                                   │     │
│  │  sessionId: "abc-123"                             │     │
│  │  ┌──────────────────────────────────────────┐    │     │
│  │  │ Messages (最近 30 轮):                     │    │     │
│  │  │  1. [System] 你是舆情分析助手...            │    │     │
│  │  │  2. [User]   新闻：美联储降息50基点        │    │     │
│  │  │  3. [AI]     分析：降息将刺激...            │    │     │
│  │  │  4. [User]   新闻：A股大涨3%               │    │     │
│  │  │  5. [AI]     分析：受之前降息影响...        │    │     │
│  │  │  ...                                        │    │     │
│  │  │  29. [User]  新闻：当前新闻 ← 新的          │    │     │
│  │  └──────────────────────────────────────────┘    │     │
│  └─────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────┘
```

---

## 八、高级功能扩展

### 8.1 新闻向量存储 + 语义检索（RAG）

```kotlin
/**
 * 基于向量存储的新闻记忆（RAG 模式）
 * - 将每条新闻编码为向量存入数据库
 * - 分析新新闻时，语义检索相关的历史新闻
 * - 将相关历史新闻注入 Prompt，实现精准关联
 */
@Service
class NewsVectorMemoryService(
    private val embeddingModel: EmbeddingModel,
    private val vectorStore: VectorStore,  // Redis / Elasticsearch
) {
    /**
     * 存储新闻向量
     */
    fun storeNews(title: String, content: String, newsId: String) {
        val text = "标题：$title\n内容：$content"
        val embedding = embeddingModel.embed(text).content()
        
        vectorStore.upsert(
            id = newsId,
            vector = embedding,
            metadata = mapOf(
                "title" to title,
                "content" to content,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    /**
     * 语义检索相关历史新闻
     */
    fun findRelatedNews(query: String, maxResults: Int = 5): List<String> {
        val queryEmbedding = embeddingModel.embed(query).content()
        
        val results = vectorStore.findSimilar(
            queryVector = queryEmbedding,
            maxResults = maxResults
        )
        
        return results.map { hit ->
            "📰 ${hit.metadata["title"]}\n${hit.metadata["content"]}"
        }
    }

    /**
     * 带 RAG 的分析：检索相关历史 + 发送给 AI
     */
    fun analyzeWithRAG(title: String, content: String): String {
        // 1. 语义检索相关历史新闻
        val relatedNews = findRelatedNews("$title $content", maxResults = 3)
        
        // 2. 构建增强 Prompt
        val prompt = buildString {
            appendLine("【当前新闻】")
            appendLine("标题：$title")
            appendLine("正文：$content")
            
            if (relatedNews.isNotEmpty()) {
                appendLine("\n【语义相关的历史新闻】")
                relatedNews.forEachIndexed { i, news ->
                    appendLine("(${i+1}) $news")
                }
            }
            
            appendLine("\n请综合分析以上所有新闻。")
        }
        
        // 3. 发送给 AI
        return chatModel.generate(prompt)
    }
}
```

### 8.2 新闻时间窗口记忆（只关联近期新闻）

```kotlin
/**
 * 基于时间窗口的记忆管理
 * 只保留最近 N 天的新闻在记忆中
 */
@Service
class TimeWindowMemoryService {
    private val windowDays = 7  // 只记忆最近7天
    
    fun getRelevantMemory(sessionId: String): List<ChatMessage> {
        val cutoff = Instant.now().minus(Duration.ofDays(windowDays.toLong()))
        
        return chatMemories.getOrDefault(sessionId, emptyList())
            .filter { it.timestamp.isAfter(cutoff) }
    }
}
```

---

## 九、推荐架构方案

根据 OpinionFlow 项目的现状，推荐 **分阶段实施**：

| 阶段 | 方案 | 工作量 | 效果 |
|------|------|--------|------|
| **Phase 1** | LangChain4j + 内存消息窗口 | ⭐ 1-2天 | 基础记忆功能 |
| **Phase 2** | Redis 持久化记忆 | ⭐⭐ 2-3天 | 跨会话保留 |
| **Phase 3** | 向量存储 + RAG | ⭐⭐⭐ 5-7天 | 语义级关联分析 |

### Phase 1 最小改动（推荐先做）

1. 添加 LangChain4j 依赖
2. 创建 `AiMemoryService`，用 `MessageWindowChatMemory`
3. 创建 `/api/ai-memory/*` 接口
4. 前端携带 `sessionId` 复用会话

### 关键配置

```properties
# application.properties
langchain4j.open-ai.chat-model.api-key=${AI_API_KEY}
langchain4j.open-ai.chat-model.base-url=https://api.deepseek.com
langchain4j.open-ai.chat-model.model-name=deepseek-v4-flash
```

---

> 本文档描述了 OpinionFlow 项目集成 LangChain4j 实现 AI 记忆化的完整方案。
> 建议从 Phase 1 开始，逐步迭代到 RAG 模式。