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
   │ (:9201)  │  │ (:9202)  │ │ (:9203)  │ │ (:9204)  │ │ (:9205)  │
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
| POST | `/api/ai/world-map-agent` | 世界格局地图 Agent（自然语言 → 结构化 JSON） |
| GET | `/api/ai/settings` | AI 配置概况：生效配置、服务端默认 token（脱敏）、厂商预置清单 |
| GET | `/api/ai/models` | 当前 token 下**可调用的模型清单**（真实调用 `{baseUrl}/models`，失败回退预置清单） |

#### 前端「AI 设置」的配置覆盖（优先级：请求头 > 服务端默认）

前端顶栏 `⚙️ AI 设置` 弹窗填写的 api-token / 接口地址 / 模型，通过请求头传给后端，**优先级高于 `key.properties`**：

| 请求头 | 说明 |
|--------|------|
| `X-AI-Provider` | 厂商标识（deepseek / openai / siliconflow / dashscope / moonshot / zhipu / openrouter / custom） |
| `X-AI-Base-Url` | OpenAI 兼容接口地址（`https://api.deepseek.com`、`.../v1`、`.../v1/chat/completions` 均可，自动归一化） |
| `X-AI-Api-Key` | 用户自己的 api-token（覆盖服务端默认 token；仅本次请求有效，不落库不记日志） |
| `X-AI-Model` | 使用的模型名（覆盖服务端默认 model） |

- 四个 AI 接口（`/api/ai/parse`、`/api/ai/parse/stream`、`/api/ai/world-map-agent`、`/api/chat-memory/chat`）均支持；
- **不带任何头时行为与之前完全一致**（走 `opinionflow.ai.api-url / api-key / model`）；
- 请求级配置用 `AiRuntimeConfigManager`（ThreadLocal）隔离，请求结束在 `finally` 中清理，避免并发请求串用 token；
- v1 仅支持 OpenAI 兼容协议，Claude / Gemini 原生协议适配留待后续版本（见 `AiProviderPresets`）。

### 对话记忆接口（opinionflow-ai → `/api/chat-memory/`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/chat-memory/sessions` | 获取所有会话摘要列表 |
| POST | `/api/chat-memory/new-session` | 创建新会话 |
| POST | `/api/chat-memory/chat` | 带记忆流式对话（SSE；支持 `agentMode=company-expert` 中国企业专家 Agent） |
| GET | `/api/chat-memory/history` | 获取指定会话历史 |
| POST | `/api/chat-memory/clear` | 清除指定会话历史 |
| DELETE | `/api/chat-memory/session/{sessionId}` | 删除指定会话 |

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

脚本运行功能支持 4 种 key：

| Key | 说明 | 是否需要股票代码 |
|-----|------|----------------|
| `comments` | 评论爬取 | ✅ 需要 |
| `news` | 新闻爬取 | ❌ 不需要 |
| `realtime` | 实时爬取 | ❌ 不需要 |
| `finance` | AkShare 财务数据查询（中国企业专家 Agent 使用） | ✅ 需要（`symbol` 参数） |

每个 key 支持配置多个脚本路径（用英文逗号或分号分隔），按顺序依次执行。

> **finance 脚本示例**：`dev/scripts/akshare_finance.py`，支持 `--symbol 600519 --mode info|spot|hist|news|industry [--start 20240101] [--end 20241231]`。
> 需要在 `key.properties` 中配置 `opinionflow.scripts.finance-path=D:/.../akshare_finance.py`。

---

## 🇨🇳 中国企业专家 Agent

AI 服务在「企业 → 中国企业」页面（`opinionflow-vue/src/views/CompanyChina.vue`）提供**中国企业专家 Agent**，AI 可自主调用以下工具：

| 工具 | 数据来源 | 说明 |
|------|---------|------|
| `queryCompany` / `queryCompanyDetail` | opinionflow-company（company_china 库） | 公司基础信息、财报列表 |
| `queryIncomeStatement` / `queryBalanceSheet` / `queryCashFlow` | opinionflow-company | 利润表 / 资产负债表 / 现金流量表 |
| `queryFinancialIndicators` / `queryIndicatorHistory` | opinionflow-company | 财务指标与历史走势 |
| `queryPeerCompare` | opinionflow-company | 同行业指标横向对比 |
| `searchFinanceNews` / `searchGeneralNews` / `getFinanceNewsDetail` | opinionflow-news 新闻库 | 检索已采集新闻 |
| `webSearch` | Tavily 联网搜索 | 实时外部信息 |
| `tushareQuery` | Tushare HTTP API | 股票历史行情、个股财务等（需 token） |
| `sinaQuote` | 新浪财经 HTTP API | 免费实时行情 |
| `akshareQuery` | AkShare Python（经 spider 脚本） | 个股信息/行情/新闻/行业数据 |

### 如何启用

**前端（已接入）**：「企业 → 中国企业」页面顶部内置 **🤖 中国企业专家 Agent** 面板，无需手工调用接口：

- 入口：公司列表行「🤖 分析」、详情面板「🤖 AI 分析」→ 把该公司设为对话目标（会话 id 自动切换为 `company_china_<股票代码>`）并预填问题；
- 发送：输入问题后 `Ctrl+Enter` 或「发送给 Agent」；AI 流式输出（SSE）；
- 会话隔离：按公司建会话（`company_china_<股票代码>`），同一家公司多轮追问共享记忆；点击历史公司自动切会话并载入 MySQL 历史；
- 面板按钮：「新对话」（切到带时间戳的新会话，不删历史）/「刷新对话」（重载历史）/「刷新会话」（重列 `company_china*` 会话）/「清除记忆」（清 MySQL+Redis）/「收起」；
- Tushare token 可选：填在面板输入框内，仅存浏览器 localStorage，随请求 `externalApiKeys.tushareToken` 传入；留空则用 `key.properties` 配置。

相关前端文件：

| 文件 | 作用 |
|------|------|
| `opinionflow-vue/src/views/CompanyChina.vue` | Agent 面板 UI（消息气泡复用 `assets/main.css` 的 `wechat*` 样式，含深色模式） |
| `opinionflow-vue/src/stores/CompanyAgentStore.js` | Agent 状态：会话隔离 / 消息流 / 历史会话 / key 管理 |
| `opinionflow-vue/src/lib/api.js` → `chatMemoryStream` | 请求封送（新增 `agentMode`、`externalApiKeys` 参数） |

**接口直调（调试用）**：`POST /api/chat-memory/chat`（网关路由规则为 `/api/chat-memory/**`），请求体参数：

```json
{
  "sessionId": "company_china_600519",
  "content": "分析一下贵州茅台的财务状况和近期新闻",
  "agentMode": "company-expert",
  "externalApiKeys": { "tushareToken": "optional-tushare-token" }
}
```

- `agentMode`：`"company-expert"` 开启中国企业专家 Agent；不传则维持原有行为（`webSearch=true` 时绑定 Tavily）。
- `externalApiKeys`：可选，本次请求有效（不持久化），优先级高于配置文件；Tushare token 未传入时回退到 `key.properties` 中的 `opinionflow.finance.tushare-token`。

### 配置项

| 配置 | 位置 | 说明 |
|------|------|------|
| `opinionflow.finance.tushare-token` | `key.properties` | Tushare token（前端传入可覆盖） |
| `opinionflow.finance.tushare-base-url` | `key.properties` | Tushare 接口地址（默认 `https://api.tushare.pro`） |
| `opinionflow.finance.sina-base-url` | `key.properties` | 新浪行情接口（默认 `https://hq.sinajs.cn/list=`） |
| `opinionflow.scripts.finance-path` | `key.properties` | AkShare 财务脚本路径 |

### 服务间调用

AI 服务通过 Feign 调用其他服务（新增 Feign 客户端均放在 `opinionflow-api` 模块）：

| Feign 客户端 | 目标服务 | 用途 |
|-------------|---------|------|
| `CompanyFeignClient` | opinionflow-company | 企业财报（company_china） |
| `CompanyUsFeignClient` | opinionflow-company | 美股财报（company_us，path `/api/company/us`） |
| `NewsFeignClient` | opinionflow-news | 新闻库检索 |
| `SpiderScriptFeignClient` | opinionflow-spider | AkShare 脚本触发 |

---

## 🇺🇸 美国企业专家 Agent（美股 / company_us）

AI 服务在「企业 → 美国企业」页面提供**美国企业专家 Agent**，功能与中国企业页面完全一致（列表 / 详情 / 三表 / 财务指标 / 走势图 / 同行业对比 / 🤖 Agent 面板），数据源换成美股库 `company_us`（US GAAP / SEC EDGAR）：

| 项目 | 中国企业 | 美国企业 |
|------|---------|---------|
| 前端路由 | `/company/china` | `/company/us` |
| 页面组件 | `views/CompanyChina.vue` | `views/CompanyUS.vue` |
| 行业下拉 Store | `stores/CompanyStore.js` | `stores/CompanyUSStore.js` |
| Agent Store | `stores/CompanyAgentStore.js` | `stores/CompanyUSAgentStore.js` |
| REST 前缀 | `/api/company` | `/api/company/us` |
| 数据库 | `company_china` | `company_us` |
| Agent 模式 | `company-expert` | `company-us-expert` |
| 会话前缀 | `company_china_<代码>` | `company_us_<代码>` |

### 后端：单服务双数据源

`opinionflow-company`（端口 9206）在同一服务内配置两个数据源；网关既有规则 `Path=/api/company/**` 已覆盖 `/api/company/us/**`，**无需新增网关路由**：

| 数据源 | 配置项 | 实体 / 仓库包 | 事务管理器 |
|--------|--------|--------------|-----------|
| `company_china`（@Primary） | `spring.datasource` | `company.domain` / `company.repo` | `transactionManager` |
| `company_us` | `spring.datasource.us` | `company.us.domain` / `company.us.repo` | `usTransactionManager` |

- 配置类：`company/config/ChinaDataSourceConfig.kt`、`company/config/UsDataSourceConfig.kt`
- `CompanyApplication` 排除 `DataSourceAutoConfiguration` / `HibernateJpaAutoConfiguration` / `JpaRepositoriesAutoConfiguration`（两套 DataSource / EMF / 仓库全部手动装配，见类注释）
- REST 控制器：`company/us/controller/CompanyUsController.kt`（14 个接口，与中国库 `CompanyController` 一一对应）
- 建表脚本：`sql/V9__create_company_us.sql`

**字段映射（保证前端与中国页面共用同一套渲染）**：美股库 `company.ticker` → 接口 `companyCode`；美股库无 `note_ref`，接口固定返回 `noteRef: null`；并额外返回 `sector / cik / isin / country / currency / formType / accessionNo / filingUrl / scaleFactor / itemCode / itemNameEn / indicatorNameEn` 等美股专有字段。

### AI 侧

- `opinionflow-api` → `CompanyUsFeignClient`（`@FeignClient(name = "opinionflow-company", contextId = "companyUsFeignClient", path = "/api/company/us")`）
- `opinionflow-ai` → `CompanyUsFinanceTool`（8 个美股财报工具，措辞与口径为美股）
- `ChatMemoryService` 抽出通用执行流程 `chatWithExpertTools(tag, …)`，`company-expert` 与 `company-us-expert` 两种模式共用「工具决策（非流式，最多 5 轮）+ 最终流式回复」逻辑

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