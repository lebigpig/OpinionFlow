# OpinionFlow 后端架构文档

## 1. 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin |
| 框架 | Spring Boot (Web, JPA) |
| 数据库 | MySQL 8.x |
| ORM | Spring Data JPA / Hibernate |
| AI 集成 | DeepSeek (OpenAI 兼容 API) |
| 脚本引擎 | Python (外部进程调用) |
| 构建工具 | Gradle (Kotlin DSL) |
| 序列化 | Jackson (JSON) |

## 2. 项目结构

```
com.lespider.opinionflow
├── OpinionFlowApplication.kt          # Spring Boot 启动入口
├── config/                            # 配置层
│   ├── JacksonConfig.kt               # Jackson 序列化配置
│   └── WebConfig.kt                   # CORS 跨域配置
├── domain/                            # 领域模型层 (JPA Entity)
│   ├── NewsConstants.kt               # 新闻常量定义
│   ├── StockComment.kt                # 股吧评论实体
│   ├── WyNews.kt                      # 网易新闻实体
│   ├── FalshNews.kt                   # 快讯实体
│   ├── YahooFinanceNews.kt            # 雅虎财经新闻实体
│   └── NewYorkNews.kt                 # 纽约新闻实体
├── repo/                              # 数据访问层 (Spring Data JPA)
│   ├── StockCommentRepository.kt
│   ├── WyNewsRepository.kt
│   ├── FalshNewsRepository.kt
│   ├── YahooFinanceNewsRepository.kt
│   └── NewYorkNewsRepository.kt
├── service/                           # 业务逻辑层
│   ├── AiParseService.kt              # AI 解析服务 (DeepSeek API)
│   ├── NewsService.kt                 # 新闻聚合查询服务
│   ├── NewYorkNewsService.kt          # 纽约新闻服务
│   ├── ScriptRunnerService.kt         # Python 脚本执行引擎
│   ├── StockCommentService.kt         # 股吧评论服务
│   └── YahooFinanceNewsService.kt     # 雅虎财经新闻服务
└── web/                               # 控制器层 (REST API)
    ├── AiController.kt                # AI 解析接口
    ├── AiAnswerController.kt          # AI 回答存储接口
    ├── EchartController.kt            # 图表数据接口
    ├── NewsController.kt              # 新闻查询接口
    ├── NewYorkNewsController.kt       # 纽约新闻接口
    ├── ScriptController.kt            # 脚本执行接口
    ├── StockCommentController.kt      # 股吧评论接口
    ├── YahooFinanceNewsController.kt  # 雅虎财经接口
    └── dto/                           # 数据传输对象
        ├── AiParseRequest.kt
        ├── AiParseResponse.kt
        ├── AiAnswerListResponse.kt
        ├── SaveAiAnswerRequest.kt
        ├── SaveAiAnswerResponse.kt
        ├── IdListResponse.kt
        ├── NewsDetailDto.kt
        ├── NewsSummaryDto.kt
        ├── NewYorkNewsDto.kt
        ├── YahooFinanceNewsDto.kt
        ├── StockCommentDetailDto.kt
        ├── StockCommentSummaryDto.kt
        ├── PageDto.kt
        ├── ScriptRunRequest.kt
        ├── ScriptRunResponse.kt
        ├── ScriptRunAllRequest.kt
        ├── ScriptRunAllResponse.kt
        ├── SaveEchartRequest.kt
        └── SaveEchartResponse.kt
```

## 3. 架构分层图

```
┌─────────────────────────────────────────────────────────────┐
│                      前端 (Vue 3)                            │
│                   opinionflow-vue                           │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP REST API
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    Controller 层 (Web)                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │AiControl-│ │NewsContr-│ │ScriptCo- │ │AiAnswerCont- │   │
│  │ ler      │ │oller     │ │ntroller  │ │   roller      │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘   │
│  ┌──────────────┐ ┌───────────┐ ┌──────────────────────┐   │
│  │StockComment- │ │EchartCo-  │ │YahooFinanceNewsCo-   │   │
│  │Controller    │ │ntroller   │ │ntroller              │   │
│  └──────────────┘ └───────────┘ └──────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    Service 层 (业务逻辑)                     │
│  ┌──────────────────┐  ┌─────────────────────────────────┐  │
│  │  AiParseService  │  │    ScriptRunnerService           │  │
│  │  - parse()       │  │    - run(key, code?)             │  │
│  │  - parseStream() │  │    - runStream(key, code?)       │  │
│  └────────┬─────────┘  └──────────────┬──────────────────┘  │
│           │                           │                     │
│  ┌────────┴─────────┐  ┌──────────────┴──────────────────┐  │
│  │  NewsService     │  │  StockCommentService            │  │
│  │  NewYorkNewsSvc  │  │  YahooFinanceNewsService        │  │
│  └────────┬─────────┘  └──────────────┬──────────────────┘  │
└───────────┼───────────────────────────┼─────────────────────┘
            │                           │
            ▼                           ▼
┌───────────────────────┐  ┌────────────────────────────────┐
│   Repository 层       │  │    外部系统                      │
│   (Spring Data JPA)  │  │  ┌──────────────────────────┐  │
│  ┌────────────────┐  │  │  │  DeepSeek API            │  │
│  │ StockCommentR  │  │  │  │  (OpenAI 兼容)            │  │
│  │ WyNewsRepo     │  │  │  └──────────────────────────┘  │
│  │ FalshNewsRepo  │  │  │  ┌──────────────────────────┐  │
│  │ YahooFinanceN  │  │  │  │  Python 爬虫脚本          │  │
│  │ NewYorkNewsR   │  │  │  │  - 东方财富股吧爬虫        │  │
│  └────────────────┘  │  │  │  - 网易新闻爬虫            │  │
└───────────┬───────────┘  │  │  - 新浪实时爬虫            │  │
            │              │  └──────────────────────────┘  │
            ▼              └────────────────────────────────┘
┌───────────────────────┐
│     MySQL 数据库       │
│   database: spider    │
│   tables: stock_      │
│   comments, wy_news,  │
│   falsh_news, ...     │
└───────────────────────┘
```

## 4. 核心模块详解

### 4.1 AI 解析模块 (`AiParseService`)

**职责**: 调用 DeepSeek (OpenAI 兼容) API 进行舆情分析

| 方法 | 说明 |
|------|------|
| `parse(content, systemPrompt?)` | 同步调用 AI 接口，返回完整分析结果 |
| `parseStream(content, systemPrompt?, onDelta)` | SSE 流式调用，逐片段返回分析内容 |

**配置项** (application.properties):
```
opinionflow.ai.api-url=https://api.deepseek.com
opinionflow.ai.api-key=sk-xxx
opinionflow.ai.model=deepseek-v4-flash
```

**默认 System Prompt**:
> 你是舆情与新闻分析助手。请阅读用户给出的正文，输出：要点摘要、情绪/立场倾向（如有）、关键词与可跟进建议。使用中文，条理清晰。

### 4.2 脚本执行模块 (`ScriptRunnerService`)

**职责**: 调度外部 Python 爬虫脚本执行

| 方法 | 说明 |
|------|------|
| `run(key, code?)` | 同步执行脚本，等待完成后返回结果 |
| `runStream(key, code?, onMeta, onStdout, onStderr)` | 流式执行，实时回调输出 |

**支持的脚本 Key**:

| Key | 用途 | 配置项 |
|-----|------|--------|
| `comments` | 股吧评论爬取 | `opinionflow.scripts.comment-path` |
| `news` | 新闻爬取 (多脚本逗号分隔) | `opinionflow.scripts.news-path` |
| `realtime` | 实时新闻爬取 | `opinionflow.scripts.realtime-path` |

**特性**:
- 支持单个 key 配置多个脚本（逗号/分号分隔，顺序执行）
- `comments` key 支持 `--code` 参数传入股票代码
- 超时限制: 20 分钟
- UTF-8 编码，自动修复中文路径乱码

### 4.3 新闻查询模块 (`NewsService`)

**职责**: 聚合查询多种新闻数据源

**支持的新闻类型**:
- **DeepSeek 菜单** — AI 分析后的新闻
- **综合新闻** (General)
- **财经新闻** (Finance)

**查询特性**: 分页 + 时间范围过滤 + 关键词搜索

### 4.4 AI 回答存储模块 (`AiAnswerController`)

**职责**: 将 AI 分析结果保存为 `.txt` 文件到前端项目目录

**文件存储路径**: `../opinionflow-vue/src/AI_answer/`

**安全措施**:
- 文件名清洗（防路径注入）
- 文件名最长 120 字符
- 拒绝 `..` 路径穿越
- 仅允许 `.txt` 后缀

## 5. 数据库 ER 图

```
┌──────────────────────┐     ┌──────────────────────┐
│    stock_comments     │     │      wy_news          │
├──────────────────────┤     ├──────────────────────┤
│ id (PK)              │     │ id (PK)              │
│ code                 │     │ title                │
│ title                │     │ content              │
│ content              │     │ publish_time         │
│ publish_time         │     │ source               │
│ source               │     │ category             │
│ created_at           │     │ created_at           │
└──────────────────────┘     └──────────────────────┘

┌──────────────────────┐     ┌──────────────────────┐
│    falsh_news         │     │ yahoo_finance_news    │
├──────────────────────┤     ├──────────────────────┤
│ id (PK)              │     │ id (PK)              │
│ title                │     │ title                │
│ content              │     │ content              │
│ publish_time         │     │ url                  │
│ source               │     │ publish_time         │
│ created_at           │     │ source               │
└──────────────────────┘     │ created_at           │
                             └──────────────────────┘

┌──────────────────────┐
│   new_york_news       │
├──────────────────────┤
│ id (PK)              │
│ title                │
│ content              │
│ publish_time         │
│ source               │
│ created_at           │
└──────────────────────┘
```

## 6. REST API 接口总览

### 6.1 AI 接口 (`/api/ai`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/parse` | 同步 AI 解析 |
| POST | `/api/ai/parse/stream` | SSE 流式 AI 解析 |

### 6.2 AI 回答存储 (`/api/ai-answer`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai-answer/save` | 保存 AI 回答到文件 |
| GET | `/api/ai-answer/list` | 列出所有已保存的 AI 回答 |
| GET | `/api/ai-answer/read/{filename}` | 读取指定 AI 回答文件 |

### 6.3 新闻接口 (`/api/news`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/news/deepseek-menu` | 分页查询 DeepSeek 分析新闻 |
| GET | `/api/news/general` | 分页查询综合新闻 |
| GET | `/api/news/finance` | 分页查询财经新闻 |
| GET | `/api/news/finance/ids` | 获取财经新闻 ID 列表 |
| GET | `/api/news/finance/{id}` | 获取单条财经新闻详情 |
| GET | `/api/news/{id}` | 获取单条新闻详情 |

### 6.4 股吧评论接口 (`/api/stock-comments`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/stock-comments/...` | 股吧评论查询 (分页/详情) |

### 6.5 脚本执行接口 (`/api/scripts`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/scripts/run` | 执行指定脚本 |
| POST | `/api/scripts/run-all` | 执行全部脚本 |
| (SSE) | 流式执行 | 实时输出脚本执行过程 |

### 6.6 图表数据接口 (`/api/echart`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST/GET | `/api/echart/...` | 保存/查询图表数据 |

### 6.7 纽约新闻接口 (`/api/new-york-news`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/new-york-news/...` | 纽约新闻查询 |

### 6.8 雅虎财经接口 (`/api/yahoo-finance-news`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/yahoo-finance-news/...` | 雅虎财经新闻查询 |

## 7. 配置说明

### 7.1 数据库配置

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/spider
spring.datasource.username=root
spring.datasource.password=${key.properties}
spring.jpa.hibernate.ddl-auto=none
```

### 7.2 AI 配置

```properties
opinionflow.ai.api-url=https://api.deepseek.com
opinionflow.ai.api-key=${key.properties}    # 从 key.properties 读取
opinionflow.ai.model=deepseek-v4-flash
```

### 7.3 脚本配置

```properties
opinionflow.scripts.enabled=true
opinionflow.scripts.python=D:/python_frame/Js_reverse/.venv/Scripts/python.exe
opinionflow.scripts.comment-path=<爬虫脚本路径>
opinionflow.scripts.news-path=<新闻脚本路径1>,<新闻脚本路径2>
opinionflow.scripts.realtime-path=<实时爬虫脚本路径>
```

## 8. 架构流程图

### 8.1 舆情分析流程

```
用户输入 → 前端 → POST /api/ai/parse (或 /parse/stream)
                        │
                        ▼
                  AiController
                        │
                        ▼
                  AiParseService.parse()
                        │
                        ▼
                  构建 OpenAI 兼容请求
                  (model + messages + system prompt)
                        │
                        ▼
                  HTTP POST → DeepSeek API
                  (https://api.deepseek.com/v1/chat/completions)
                        │
                        ▼
                  解析响应 → 返回分析结果
                        │
                        ▼
                  POST /api/ai-answer/save → 保存为 .txt 文件
                  (保存到 opinionflow-vue/src/AI_answer/)
```

### 8.2 爬虫执行流程

```
用户点击 "运行爬虫" → 前端 → POST /api/scripts/run
                              │
                              ▼
                        ScriptController
                              │
                              ▼
                        ScriptRunnerService.run(key, code?)
                              │
                              ├── key="comments" → 执行股吧爬虫脚本
                              ├── key="news"     → 顺序执行新闻爬虫脚本们
                              └── key="realtime" → 执行实时爬虫脚本
                                    │
                                    ▼
                              ProcessBuilder 启动 Python 进程
                              (超时: 20分钟)
                                    │
                                    ▼
                              stdout/stderr → 返回执行结果
                                    │
                                    ▼
                              爬虫数据写入 MySQL (spider 数据库)
```

## 9. 外部依赖关系

```
┌─────────────────────────────────────────────────────────┐
│                    OpinionFlow Backend                    │
└───────────┬──────────┬───────────┬──────────────────────┘
            │          │           │
            ▼          ▼           ▼
     ┌────────────┐ ┌──────┐ ┌──────────────────┐
     │  DeepSeek  │ │MySQL │ │  Python 环境      │
     │  API       │ │3306  │ │  (.venv)          │
     │            │ │      │ │                    │
     │ /v1/chat/  │ │spider│ │ 东方财富股吧爬虫   │
     │ completions│ │  DB  │ │ 网易新闻爬虫       │
     └────────────┘ └──────┘ │ 新浪实时爬虫       │
                             │ 雅虎新闻爬虫       │
                             └──────────────────┘
```

## 10. 安全设计

| 安全点 | 实现方式 |
|--------|----------|
| API Key 保护 | 存储在 `key.properties`（已被 .gitignore 排除） |
| 文件名注入防护 | `sanitizeFilename()` 清洗，拒绝 `..`、特殊字符 |
| 路径穿越防护 | `normalize()` + `startsWith()` 校验 |
| CORS 跨域 | `WebConfig.kt` 配置允许的前端域名 |
| 脚本执行超时 | 20 分钟超时，超时后 `destroyForcibly()` |
| 脚本执行限制 | `opinionflow.scripts.enabled` 开关控制 |

## 11. 关键配置文件

| 文件 | 说明 |
|------|------|
| `application.properties` | 主配置文件（提交到 Git，不含密钥） |
| `key.properties` | 密钥配置文件（已加入 .gitignore，不提交） |
| `build.gradle.kts` | Gradle 构建配置 |
| `settings.gradle.kts` | Gradle 项目设置 |

---

> 本文档由 Cline 根据 OpinionFlow 后端源码自动生成，最后更新: 2026-05-11