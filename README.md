<div align="center">

# 🧠 OpinionFlow — Cloud 后端

**舆情分析与新闻聚合平台 · 微服务后端**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-purple)](https://kotlinlang.org/)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2024.0.1-blue)](https://spring.io/projects/spring-cloud)
[![Nacos](https://img.shields.io/badge/Nacos-2.x-00AA00)](https://nacos.io/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-orange)](https://docs.spring.io/spring-ai/reference/)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

</div>

---

## 📋 项目简介

OpinionFlow Cloud 是一个基于 **Spring Cloud** 微服务架构的舆情分析后端，集成 Nacos 注册中心、Gateway 网关、RAG 向量检索、AI 智能分析等能力。核心功能包括：

- 📰 **多源新闻聚合** — 网易新闻、纽约时报、雅虎财经、实时财经快讯
- 🤖 **AI 智能分析** — 接入 OpenAI 兼容 API（DeepSeek / ChatGPT 等），支持多轮对话与记忆
- 🔍 **联网搜索** — 集成 Tavily 搜索引擎，AI 可实时检索互联网信息
- 📚 **RAG 知识增强** — 基于 Milvus 向量数据库的检索增强生成
- 📊 **可视化图表** — ECharts 图表数据持久化与管理
- 🐍 **脚本调度引擎** — 集成 Python 爬虫脚本，支持 SSE 实时日志流
- 💬 **股吧评论分析** — 对股票评论进行情绪分析、主题提取

---

## 🏗️ 微服务架构

<!-- 📸 截图标记：系统架构图 -->
> **系统架构图（请替换为实际截图）**

<!-- 📸 REPLACE: 架构图截图 -->
![系统架构](screenshots/architecture.png)

```
                          ┌─────────────────────┐
                          │   Vue 3 前端 (:5173)  │
                          └──────────┬──────────┘
                                     │
                          ┌──────────▼──────────┐
                          │  Gateway 网关 (:9006) │
                          └──────────┬──────────┘
                                     │
           ┌─────────────┬───────────┼───────────┬──────────────┐
           ▼             ▼           ▼           ▼              ▼
   ┌──────────┐  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
   │ News     │  │ AI       │ │ Spider   │ │ Echart   │ │ RAG      │
   │ (:9201)  │  │ (:9202)  │ │ (:9203)  │ │          │ │          │
   └──────────┘  └──────────┘ └──────────┘ └──────────┘ └──────────┘
        │             │           │           │              │
        └─────────────┴───────────┴───────────┴──────────────┘
                                     │
                    ┌────────────────┼────────────────┐
                    ▼                ▼                ▼
              ┌──────────┐    ┌──────────┐    ┌──────────┐
              │  MySQL   │    │  Redis   │    │  Milvus  │
              └──────────┘    └──────────┘    └──────────┘
```

---

## 📦 模块说明

| 模块 | 端口 | 说明 |
|------|------|------|
| **opinionflow-gateway** | 9006 | Spring Cloud Gateway 网关，统一路由 & CORS |
| **opinionflow-news** | 9201 | 新闻服务（网易、纽约时报、雅虎财经） |
| **opinionflow-ai** | 9202 | AI 分析服务（DeepSeek/ChatGPT、对话记忆、Tavily 搜索、RAG 增强） |
| **opinionflow-spider** | 9203 | 爬虫服务（脚本调度、评论分析、搜索结果持久化） |
| **opinionflow-echart** | 9204 | 图表服务（ECharts JSON 持久化） |
| **opinionflow-rag** | 9205 | RAG 服务（Milvus 向量检索、新闻向量导入） |
| **opinionflow-common** | — | 公共模块（CORS、Jackson、全局异常处理、统一响应） |
| **opinionflow-api** | — | Feign 客户端接口（服务间调用） |

---

## 📡 网关路由

所有请求通过 Gateway（`:9006`）统一入口：

| 路径规则 | 目标服务 | 说明 |
|----------|---------|------|
| `/api/news/**` | opinionflow-news | 新闻相关接口 |
| `/api/nytimes/**` | opinionflow-news | 纽约时报接口 |
| `/api/yahoo/**` | opinionflow-news | 雅虎财经接口 |
| `/api/ai/**` | opinionflow-ai | AI 分析接口 |
| `/api/chat-memory/**` | opinionflow-ai | 对话记忆接口 |
| `/api/scripts/**` | opinionflow-spider | 脚本调度接口 |
| `/api/comments/**` | opinionflow-spider | 评论分析接口 |
| `/api/search-results/**` | opinionflow-spider | 搜索结果接口 |
| `/api/echart/**` | opinionflow-echart | 图表接口 |
| `/api/rag/**` | opinionflow-rag | RAG 检索接口 |

---

## 🏛️ 项目结构

```
OpinionFlow-Cloud/
├── key.properties                    # 敏感配置（API Key 等，不提交 Git）
├── sql/
│   └── init.sql                      # 数据库初始化脚本
├── opinionflow-common/               # 公共模块
│   └── src/main/kotlin/.../common/
│       ├── config/                   # CORS、Jackson 配置
│       └── core/                     # 统一响应 Result、分页、全局异常
├── opinionflow-api/                  # Feign 接口模块
│   └── src/main/kotlin/.../api/
│       └── rag/RagFeignClient.kt     # RAG 服务 Feign 客户端
├── opinionflow-gateway/              # 网关服务
│   └── src/main/resources/
│       └── application.yml           # 路由配置
├── opinionflow-news/                 # 新闻服务
│   └── src/main/kotlin/.../news/
├── opinionflow-ai/                   # AI 服务
│   └── src/main/kotlin/.../ai/
│       ├── controller/               # AiController、ChatMemoryController
│       ├── service/                  # AiParseService、ChatMemoryService、TavilyWebSearchService
│       ├── domain/                   # ChatHistory 实体
│       └── repo/                     # ChatHistoryRepository
├── opinionflow-spider/               # 爬虫服务
│   └── src/main/kotlin/.../spider/
│       ├── controller/               # ScriptController、StockCommentController、SearchResultController
│       ├── service/                  # ScriptRunnerService、StockCommentService、SearchResultService
│       ├── domain/                   # StockComment、SearchResult 实体
│       └── repo/                     # StockCommentRepository、SearchResultRepository
├── opinionflow-echart/               # 图表服务
│   └── src/main/kotlin/.../echart/
│       ├── controller/               # EchartController
│       ├── service/                  # EchartService、EchartFileService
│       └── domain/                   # EchartData 实体
├── opinionflow-rag/                  # RAG 服务
│   └── src/main/kotlin/.../rag/
│       ├── config/                   # MilvusConfig
│       ├── controller/               # RagController
│       ├── service/                  # MilvusNewsImportService
│       ├── runner/                   # MilvusImportRunner（启动时自动导入）
│       └── domain/                   # WyNews、FalshNews、YahooFinanceNews 等
├── build.gradle.kts                  # 根构建脚本
├── settings.gradle.kts               # 模块声明
└── gradle.properties                 # Gradle 配置
```

---

## 🚀 快速开始

### 环境要求

| 工具 | 版本要求 |
|------|---------|
| JDK | 17+ |
| Kotlin | 2.1.x |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| Nacos | 2.x（服务注册与配置中心） |
| Milvus | 2.x（可选，RAG 向量数据库） |
| Python（可选） | 3.x（运行爬虫脚本时需要） |

<!-- 📸 截图标记：Nacos 服务列表 -->
> **Nacos 服务注册列表（请替换为实际截图）**

<!-- 📸 REPLACE: Nacos 截图 -->
![Nacos 服务列表](screenshots/nacos-services.png)

### 1️⃣ 克隆项目

```bash
git clone https://github.com/lebigpig/OpinionFlow.git
cd OpinionFlow
```

### 2️⃣ 启动基础设施

确保以下服务已启动：

```bash
# MySQL（创建数据库）
mysql -u root -p -e "CREATE DATABASE spider DEFAULT CHARSET utf8mb4;"
mysql -u root -p spider < sql/init.sql

# Redis
redis-server

# Nacos（单机模式）
sh startup.sh -m standalone
```

### 3️⃣ 配置 key.properties

在项目根目录创建 `key.properties`（已被 `.gitignore` 忽略）：

```properties
# ===== AI 接口配置 =====
opinionflow.ai.api-url=https://api.deepseek.com
opinionflow.ai.api-key=sk-your-api-key
opinionflow.ai.model=deepseek-chat

# ===== Tavily 搜索配置（可选） =====
tavily.api-key=tvly-your-api-key

# ===== 脚本运行配置（可选） =====
opinionflow.scripts.python=你的Python解释器路径
opinionflow.scripts.comment-path=你的评论爬取脚本路径
opinionflow.scripts.news-path=你的新闻爬取脚本路径（逗号分隔）
opinionflow.scripts.realtime-path=你的实时爬取脚本路径
```

### 4️⃣ 配置各服务数据库连接

修改各服务的 `src/main/resources/application.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/spider?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 你的数据库密码
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: 你的Redis密码
```

> **注意**：opinionflow-news 服务支持环境变量覆盖（`DB_HOST`、`DB_PORT`、`DB_USER`、`DB_PASS` 等），方便容器化部署。

### 5️⃣ 启动服务

按以下顺序启动各微服务：

```bash
# 1. 启动公共模块（自动编译）
./gradlew :opinionflow-common:build

# 2. 启动各微服务（可并行）
./gradlew :opinionflow-news:bootRun
./gradlew :opinionflow-ai:bootRun
./gradlew :opinionflow-spider:bootRun
./gradlew :opinionflow-echart:bootRun
./gradlew :opinionflow-rag:bootRun

# 3. 最后启动网关
./gradlew :opinionflow-gateway:bootRun
```

或一键构建全部：

```bash
./gradlew build
```

### 6️⃣ 启动前端

```bash
cd ../opinionflow-vue
npm install
npm run dev
```

前端默认启动在 `http://localhost:5173`，通过网关 `http://localhost:9006` 访问后端 API。

---

## ⚙️ 配置详解

### key.properties（敏感配置，不提交 Git）

| 配置项 | 说明 | 示例值 |
|--------|------|--------|
| `opinionflow.ai.api-url` | OpenAI 兼容 API 地址 | `https://api.deepseek.com` |
| `opinionflow.ai.api-key` | API 密钥 | `sk-xxxx` |
| `opinionflow.ai.model` | AI 模型名称 | `deepseek-chat` |
| `tavily.api-key` | Tavily 搜索 API 密钥 | `tvly-xxxx` |
| `opinionflow.scripts.python` | Python 解释器路径 | `D:/python/.venv/Scripts/python.exe` |
| `opinionflow.scripts.comment-path` | 评论爬取脚本路径（逗号分隔） | `D:/scripts/crawl_comments.py` |
| `opinionflow.scripts.news-path` | 新闻爬取脚本路径（逗号分隔） | `D:/scripts/news1.py,D:/scripts/news2.py` |
| `opinionflow.scripts.realtime-path` | 实时爬取脚本路径（逗号分隔） | `D:/scripts/realtime.py` |

### Nacos 配置

各服务默认连接 `127.0.0.1:8848`，可通过环境变量 `NACOS_ADDR` 和 `NACOS_NAMESPACE` 覆盖：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
```

### AI 服务 RAG 配置

```yaml
opinionflow:
  rag:
    enabled: true              # 是否启用 RAG
    top-k: 5                   # 检索 Top-K 条相关新闻
    max-distance: 50.0         # 最大向量距离阈值
    context-prefix: "以下是与用户问题相关的新闻资料，供你参考分析："
```

### Tavily 搜索配置

```yaml
opinionflow:
  tavily:
    enabled: true
    api-key: ${tavily.api-key:}
    base-url: ${tavily.base-url:https://api.tavily.com}
    max-results: 12
    search-depth: advanced
```

---

## 🗄️ 数据库表结构

数据库名：`spider`（字符集：`utf8mb4`）

| 表名 | 说明 | 数据来源 |
|------|------|---------|
| `wynews` | 网易新闻（通用新闻） | 脚本爬取 |
| `falsh_news` | 快讯新闻（财经快讯） | 脚本爬取 |
| `new_york_news` | 纽约时报新闻 | 脚本爬取 |
| `yahoo_finance_news` | 雅虎财经新闻 | 脚本爬取 |
| `stock_comment` | 股票评论分析结果 | AI 分析后写入 |
| `chat_history` | AI 对话历史记录 | AI 对话自动保存 |
| `echart_data` | ECharts 图表数据 | 前端保存 |
| `search_result` | Tavily 搜索结果 | 搜索后持久化 |
| `milvus_import_state` | Milvus 导入状态 | RAG 自动追踪 |

详细建表语句见 [`sql/init.sql`](sql/init.sql)。

---

## 📡 完整 API 接口

### 新闻接口（opinionflow-news → `/api/news/`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/news/general` | 网易新闻列表（分页） |
| GET | `/api/news/deepseek-menu` | DeepSeek 专区新闻 |
| GET | `/api/news/finance` | 实时财经新闻 |
| GET | `/api/news/finance/ids` | 财经新闻 ID 列表 |
| GET | `/api/news/{id}` | 新闻详情 |
| GET | `/api/news/finance/{id}` | 财经新闻详情 |
| GET | `/api/yahoo/news` | 雅虎财经新闻 |
| GET | `/api/nytimes/news` | 纽约时报新闻 |

### AI 接口（opinionflow-ai → `/api/ai/`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/parse` | AI 分析（非流式） |
| POST | `/api/ai/parse/stream` | AI 分析（SSE 流式） |

### 对话记忆接口（opinionflow-ai → `/api/chat-memory/`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/chat-memory/history` | 获取对话历史列表 |
| GET | `/api/chat-memory/history/{sessionId}` | 获取指定会话历史 |
| POST | `/api/chat-memory/send` | 发送消息（SSE 流式回复） |
| DELETE | `/api/chat-memory/history/{sessionId}` | 删除指定会话 |
| DELETE | `/api/chat-memory/history` | 清空所有对话历史 |

### 爬虫 & 评论接口（opinionflow-spider → `/api/scripts/`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/scripts/run` | 运行单个脚本 |
| POST | `/api/scripts/run/stream` | 运行单个脚本（SSE 流式） |
| POST | `/api/scripts/run-all` | 并行运行全部脚本 |
| POST | `/api/scripts/run-all/stream` | 并行运行全部脚本（SSE 流式） |
| GET | `/api/comments` | 股票评论分析列表 |
| GET | `/api/comments/{id}` | 评论分析详情 |

### 搜索接口（opinionflow-spider → `/api/search-results/`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/search-results/tavily` | Tavily 联网搜索 |
| POST | `/api/search-results/save` | 保存搜索结果 |

### 图表接口（opinionflow-echart → `/api/echart/`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/echart/save` | 保存 ECharts 图表 JSON |

### RAG 接口（opinionflow-rag → `/api/rag/`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/rag/search` | 向量检索相关新闻 |

---

## 🧩 脚本运行说明

脚本运行功能支持 3 种 key：

| Key | 说明 | 是否需要股票代码 |
|-----|------|----------------|
| `comments` | 评论爬取 | ✅ 需要 |
| `news` | 新闻爬取 | ❌ 不需要 |
| `realtime` | 实时爬取 | ❌ 不需要 |

每个 key 支持配置多个脚本路径（用英文逗号或分号分隔），按顺序依次执行。

---

## 📚 RAG 知识增强

RAG 模块基于 **Milvus** 向量数据库实现：

1. **向量导入** — `MilvusImportRunner` 在服务启动时自动将新闻数据向量化并导入 Milvus
2. **向量检索** — AI 分析时自动检索相关新闻作为上下文（可通过配置开关）
3. **状态追踪** — `milvus_import_state` 表记录导入进度，避免重复导入

<!-- 📸 截图标记：RAG 流程图 -->
> **RAG 流程示意（请替换为实际截图/图表）**

<!-- 📸 REPLACE: RAG 流程截图 -->
![RAG 流程](screenshots/rag-pipeline.png)

---

## 🛠️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| **Spring Boot** | 3.4.5 | 应用框架 |
| **Spring Cloud** | 2024.0.1 | 微服务治理 |
| **Spring Cloud Alibaba** | 2023.0.3.2 | Nacos 服务注册/配置 |
| **Spring AI** | 1.0.0 | AI 集成框架 |
| **Kotlin** | 2.1.20 | 编程语言 |
| **Spring Cloud Gateway** | — | API 网关 |
| **OpenFeign** | — | 服务间调用 |
| **Spring Data JPA** | — | 数据持久化 |
| **MySQL** | 8.0+ | 关系型数据库 |
| **Redis** | 6.0+ | 缓存 / 会话 |
| **Milvus** | 2.x | 向量数据库（RAG） |
| **Spring AI Tavily** | — | 联网搜索 |
| **Gradle** | 8.x | 构建工具 |

---

## 📄 开源协议

本项目基于 MIT 协议开源，详见 [LICENSE](LICENSE) 文件。

---

## ⚠️ 注意事项

1. **API 密钥安全**：所有敏感配置在 `key.properties` 中，已被 `.gitignore` 忽略，请勿手动提交
2. **数据库密码**：各服务 `application.yml` 中的默认密码 `123456` 仅供本地开发，请在生产环境使用强密码
3. **Nacos 依赖**：所有微服务启动前必须先启动 Nacos，否则无法注册发现
4. **启动顺序**：建议先启动 infrastructure（MySQL → Redis → Nacos），再启动各微服务，最后启动 Gateway
5. **Milvus（可选）**：RAG 功能需要 Milvus，如不使用可在 `application.yml` 中设置 `opinionflow.rag.enabled: false`
6. **Tavily（可选）**：联网搜索功能需要 Tavily API Key，如不使用可忽略
7. **脚本路径**：爬虫脚本路径指向本地文件系统，不同用户需要在 `key.properties` 中自行配置