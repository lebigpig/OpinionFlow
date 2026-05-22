# OpinionFlow 微服务迁移方案

## 一、当前项目架构分析

### 1.1 当前单体架构全景

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        OpinionFlow 单体应用                              │
│                    Spring Boot 3.4.5 + Kotlin                           │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                        Controller 层（8 个）                       │  │
│  │                                                                   │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐              │  │
│  │  │NewsController│ │AiController  │ │ChatMemory    │              │  │
│  │  │/api/news     │ │/api/ai       │ │Controller    │              │  │
│  │  │              │ │              │ │/api/chat     │              │  │
│  │  │ 综合新闻查询  │ │ AI文本解析    │ │ AI对话记忆   │              │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘              │  │
│  │                                                                   │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐              │  │
│  │  │Script        │ │Echart        │ │StockComment  │              │  │
│  │  │Controller    │ │Controller    │ │Controller    │              │  │
│  │  │/api/scripts  │ │/api/echart   │ │/api/comments │              │  │
│  │  │              │ │              │ │              │              │  │
│  │  │ 爬虫脚本执行  │ │ 图表JSON管理  │ │ 股票评论查询  │              │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘              │  │
│  │                                                                   │  │
│  │  ┌──────────────┐ ┌──────────────┐                               │  │
│  │  │YahooFinance  │ │NewYorkNews   │                               │  │
│  │  │NewsController│ │Controller    │                               │  │
│  │  │/api/yahoo    │ │/api/nytimes  │                               │  │
│  │  │              │ │              │                               │  │
│  │  │ 雅虎财经新闻  │ │ 纽约时报新闻  │                               │  │
│  │  └──────────────┘ └──────────────┘                               │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                        Service 层（8 个）                          │  │
│  │                                                                   │  │
│  │  NewsService / AiParseService / ChatMemoryService                 │  │
│  │  ScriptRunnerService / StockCommentService / MilvusNewsImportService│ │
│  │  NewYorkNewsService / YahooFinanceNewsService                     │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                     数据层（5 个 Repo + Domain）                    │  │
│  │                                                                   │  │
│  │  JPA Repository ──→ MySQL (spider 库)                             │  │
│  │  Redis ──→ 对话缓存 (db3)                                         │  │
│  │  Milvus ──→ 向量数据库 (news_vectors 集合)                         │  │
│  │  文件系统 ──→ Echart JSON 文件                                     │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                     外部依赖                                       │  │
│  │                                                                   │  │
│  │  OpenAI API (DeepSeek) ──→ AI 解析 + 对话                        │  │
│  │  Python 爬虫脚本 ──→ 新闻/评论/实时数据爬取                       │  │
│  │  Milvus 向量数据库 ──→ RAG 检索增强                               │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.2 当前目录结构与业务模块对应关系

```
src/main/kotlin/com/lespider/opinionflow/
├── OpinionFlowApplication.kt          ← 启动类（单体入口）
├── config/                            ← 配置层
│   ├── JacksonConfig.kt              ← JSON 序列化配置
│   ├── MilvusConfig.kt               ← Milvus 向量数据库连接
│   └── WebConfig.kt                   ← CORS 跨域配置
├── domain/                            ← 实体层
│   ├── ChatHistory.kt                 ← AI 对话历史实体
│   ├── FalshNews.kt                   ← Flash 新闻实体
│   ├── MilvusImportState.kt           ← 向量导入状态实体
│   ├── NewsConstants.kt               ← 新闻常量
│   ├── NewYorkNews.kt                 ← 纽约时报实体
│   ├── StockComment.kt                ← 股票评论实体
│   ├── WyNews.kt                      ← 网易新闻实体
│   └── YahooFinanceNews.kt            ← 雅虎财经实体
├── repo/                              ← 数据访问层（新闻相关）
│   ├── FalshNewsRepository.kt
│   ├── MilvusImportStateRepository.kt
│   ├── NewYorkNewsRepository.kt
│   ├── StockCommentRepository.kt
│   ├── WyNewsRepository.kt
│   └── YahooFinanceNewsRepository.kt
├── repository/                        ← 数据访问层（对话相关）
│   └── ChatHistoryRepository.kt
├── runner/                            ← 启动任务
│   └── MilvusImportRunner.kt          ← 应用启动时自动导入向量
├── service/                           ← 业务逻辑层
│   ├── AiParseService.kt             ← AI 文本解析（DeepSeek API）
│   ├── ChatMemoryService.kt          ← AI 对话记忆（Redis+MySQL）
│   ├── MilvusNewsImportService.kt    ← 新闻向量化导入 Milvus
│   ├── NewsService.kt                 ← 综合新闻查询（多表聚合）
│   ├── NewYorkNewsService.kt          ← 纽约时报新闻
│   ├── ScriptRunnerService.kt         ← Python 爬虫脚本执行
│   ├── StockCommentService.kt         ← 股票评论查询
│   └── YahooFinanceNewsService.kt     ← 雅虎财经新闻
└── web/                               ← API 控制器层
    ├── AiController.kt                ← /api/ai
    ├── ChatMemoryController.kt        ← /api/chat-memory
    ├── EchartController.kt            ← /api/echart
    ├── NewsController.kt              ← /api/news
    ├── NewYorkNewsController.kt        ← /api/nytimes
    ├── ScriptController.kt            ← /api/scripts
    ├── StockCommentController.kt       ← /api/comments
    ├── YahooFinanceNewsController.kt   ← /api/yahoo
    └── dto/                            ← 请求/响应 DTO
```

### 1.3 当前外部依赖

```
┌─────────────────────────────────────────────────────────────────┐
│                      外部依赖清单                                │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 数据库                                                    │    │
│  │   MySQL 127.0.0.1:3306/spider  ← 所有业务表共用一个库    │    │
│  │   Redis 127.0.0.1:6379/db3     ← AI 对话缓存            │    │
│  │   Milvus 127.0.0.1:19530       ← 向量数据库             │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 外部 API                                                 │    │
│  │   OpenAI API (DeepSeek)  ← AI 解析 + 对话               │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ 本地资源                                                  │    │
│  │   Python 脚本 (通过 key.properties 配置路径)             │    │
│  │   Echart JSON 文件 (写入到 ../opinionflow-vue/public/)   │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 二、业务模块识别与耦合分析

### 2.1 识别出的 6 个业务模块

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  模块 ① 新闻数据服务 (News)                                     │
│  ────────────────────────                                       │
│  Controller: NewsController + NewYorkNewsController             │
│               + YahooFinanceNewsController                      │
│  Service:    NewsService + NewYorkNewsService                   │
│               + YahooFinanceNewsService                         │
│  Domain:     WyNews + FalshNews + NewYorkNews                   │
│               + YahooFinanceNews + NewsConstants                │
│  Repo:       WyNewsRepository + FalshNewsRepository             │
│               + NewYorkNewsRepository + YahooFinanceNewsRepository│
│  职责:        多源新闻的查询、分页、详情                         │
│  API:         /api/news/**, /api/nytimes/**, /api/yahoo/**     │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  模块 ② AI 服务 (AI)                                            │
│  ────────────────────────                                       │
│  Controller: AiController + ChatMemoryController                │
│  Service:    AiParseService + ChatMemoryService                 │
│  Domain:     ChatHistory                                        │
│  Repo:       ChatHistoryRepository                              │
│  外部依赖:    OpenAI/DeepSeek API + Redis 缓存                  │
│  职责:        AI 文本解析、流式对话、对话记忆管理                │
│  API:         /api/ai/**, /api/chat-memory/**                  │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  模块 ③ 股票评论服务 (StockComment)                              │
│  ────────────────────────                                       │
│  Controller: StockCommentController                             │
│  Service:    StockCommentService                                │
│  Domain:     StockComment                                       │
│  Repo:       StockCommentRepository                             │
│  职责:        股票评论数据查询                                   │
│  API:         /api/comments/**                                  │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  模块 ④ 爬虫服务 (Spider)                                       │
│  ────────────────────────                                       │
│  Controller: ScriptController                                   │
│  Service:    ScriptRunnerService                                │
│  外部依赖:    Python 爬虫脚本                                    │
│  职责:        执行 Python 爬虫（新闻、评论、实时数据）           │
│  API:         /api/scripts/**                                   │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  模块 ⑤ 向量检索服务 (RAG)                                      │
│  ────────────────────────                                       │
│  Service:    MilvusNewsImportService                            │
│  Runner:     MilvusImportRunner                                 │
│  Config:     MilvusConfig                                       │
│  Domain:     MilvusImportState                                  │
│  Repo:       MilvusImportStateRepository                        │
│  外部依赖:    Milvus 向量数据库                                  │
│  职责:        新闻向量化导入、RAG 检索增强                       │
│  无独立 API（被 AI 模块内部调用）                                │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  模块 ⑥ 图表服务 (Echart)                                       │
│  ────────────────────────                                       │
│  Controller: EchartController                                   │
│  职责:        Echart 图表 JSON 的保存、读取、列表               │
│  存储:        文件系统（写入到 Vue 项目的 public/echart/）       │
│  API:         /api/echart/**                                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 模块间耦合关系图

```
                    ┌──────────────┐
                    │  Vue 前端     │
                    └──────┬───────┘
                           │ HTTP
                           ▼
              ┌────────────────────────┐
              │      API Gateway       │
              │   (路由 + 鉴权 + 限流)  │
              └────────────────────────┘
              │      │      │      │      │      │
              ▼      ▼      ▼      ▼      ▼      ▼
         ┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐
         │ News ││  AI  ││Stock ││Spider││Echart││      │
         │      ││      ││Comment││      ││      ││      │
         └──┬───┘└──┬───┘└──────┘└──┬───┘└──────┘└──────┘
            │       │               │
            │       │  ┌────────────┘
            │       │  │  爬虫爬取新闻 → 写入 DB → 新闻模块读取
            │       │  │  爬虫爬取评论 → 写入 DB → 评论模块读取
            │       │  │  （通过数据库间接耦合）
            │       │  │
            ▼       ▼  ▼
         ┌──────────────────────────────────────────┐
         │              共享数据层                     │
         │  ┌─────────┐ ┌─────────┐ ┌─────────────┐ │
         │  │  MySQL   │ │  Redis  │ │   Milvus    │ │
         │  │ (spider) │ │  (db3)  │ │(news_vectors)│ │
         │  └─────────┘ └─────────┘ └─────────────┘ │
         └──────────────────────────────────────────┘

耦合关系：
  News ──读取──→ MySQL（与 Spider 写入相同的表）
  AI   ──调用──→ RAG 模块（向量检索）
  AI   ──读写──→ Redis + MySQL（对话缓存）
  RAG  ──读取──→ MySQL（新闻表）→ 向量化 → Milvus
  Spider──写入──→ MySQL（新闻表、评论表）
```

### 2.3 模块间依赖矩阵

```
┌──────────┬───────┬──────┬───────┬────────┬───────┬──────┐
│ 被依赖方→ │ News  │  AI  │Stock  │ Spider │ RAG   │Echart│
│ 依赖方↓   │       │      │Comment│        │       │      │
├──────────┼───────┼──────┼───────┼────────┼───────┼──────┤
│ News     │   -   │  无  │  无   │  DB共享 │  无   │  无  │
├──────────┼───────┼──────┼───────┼────────┼───────┼──────┤
│ AI       │  无   │  -   │  无   │   无   │ 调用  │  无  │
├──────────┼───────┼──────┼───────┼────────┼───────┼──────┤
│StockComm │  无   │  无  │   -   │  DB共享 │  无   │  无  │
├──────────┼───────┼──────┼───────┼────────┼───────┼──────┤
│ Spider   │  无   │  无  │  无   │   -    │  无   │  无  │
├──────────┼───────┼──────┼───────┼────────┼───────┼──────┤
│ RAG      │ DB共享│  无  │  无   │  DB共享 │   -   │  无  │
├──────────┼───────┼──────┼───────┼────────┼───────┼──────┤
│ Echart   │  无   │  无  │  无   │   无   │  无   │  -   │
└──────────┴───────┴──────┴───────┴────────┴───────┴──────┘

结论：模块间耦合度很低，主要通过数据库共享数据（间接耦合）
     只有 AI → RAG 存在直接调用关系
     非常适合拆分为微服务！
```

---

## 三、微服务拆分方案

### 3.1 拆分后的目标架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         OpinionFlow 微服务架构                               │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                          基础设施层                                    │  │
│  │                                                                       │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐              │  │
│  │  │ Nacos    │  │ Redis    │  │ MySQL    │  │ Milvus   │              │  │
│  │  │ 注册中心  │  │ 缓存     │  │ 数据库    │  │ 向量数据库│              │  │
│  │  │ 配置中心  │  │          │  │          │  │          │              │  │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘              │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                          网关层                                        │  │
│  │                                                                       │  │
│  │  ┌────────────────────────────────────────────────────────────────┐   │  │
│  │  │                    opinionflow-gateway                         │   │  │
│  │  │                                                                │   │  │
│  │  │   路由转发 │ 限流熔断 │ 统一鉴权 │ 请求日志 │ 跨域处理         │   │  │
│  │  │                                                                │   │  │
│  │  │   路由规则：                                                    │   │  │
│  │  │     /api/news/**      → opinionflow-news                      │   │  │
│  │  │     /api/ai/**        → opinionflow-ai                        │   │  │
│  │  │     /api/chat-memory/** → opinionflow-ai                      │   │  │
│  │  │     /api/comments/**  → opinionflow-spider                    │   │  │
│  │  │     /api/scripts/**   → opinionflow-spider                    │   │  │
│  │  │     /api/echart/**    → opinionflow-echart                    │   │  │
│  │  │     /api/nytimes/**   → opinionflow-news                      │   │  │
│  │  │     /api/yahoo/**     → opinionflow-news                      │   │  │
│  │  └────────────────────────────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                          业务服务层                                    │  │
│  │                                                                       │  │
│  │  ┌─────────────────────┐  ┌─────────────────────┐                    │  │
│  │  │ opinionflow-news    │  │ opinionflow-ai      │                    │  │
│  │  │ ──────────────────  │  │ ──────────────────  │                    │  │
│  │  │ 多源新闻查询服务     │  │ AI 解析 + 对话服务   │                    │  │
│  │  │                     │  │                     │                    │  │
│  │  │ NewsController      │  │ AiController        │                    │  │
│  │  │ NewYorkNewsController│ │ ChatMemoryController │                    │  │
│  │  │ YahooFinanceNews    │  │                     │                    │  │
│  │  │   Controller        │  │ AiParseService      │                    │  │
│  │  │                     │  │ ChatMemoryService   │                    │  │
│  │  │ NewsService         │  │                     │                    │  │
│  │  │ NewYorkNewsService  │  │ 依赖：Redis + MySQL │                    │  │
│  │  │ YahooFinanceNews    │  │ 调用：RAG 服务       │                    │  │
│  │  │   Service           │  │ 外部：DeepSeek API  │                    │  │
│  │  │                     │  │                     │                    │  │
│  │  │ 依赖：MySQL         │  │ 端口：9202          │                    │  │
│  │  │ 端口：9201          │  │                     │                    │  │
│  │  └─────────────────────┘  └─────────────────────┘                    │  │
│  │                                                                       │  │
│  │  ┌─────────────────────┐  ┌─────────────────────┐                    │  │
│  │  │ opinionflow-spider  │  │ opinionflow-echart  │                    │  │
│  │  │ ──────────────────  │  │ ──────────────────  │                    │  │
│  │  │ 爬虫执行 + 评论服务  │  │ 图表管理服务         │                    │  │
│  │  │                     │  │                     │                    │  │
│  │  │ ScriptController    │  │ EchartController    │                    │  │
│  │  │ StockComment        │  │                     │                    │  │
│  │  │   Controller        │  │ 文件存储（OSS/本地） │                    │  │
│  │  │                     │  │                     │                    │  │
│  │  │ ScriptRunnerService │  │ 依赖：文件系统       │                    │  │
│  │  │ StockCommentService │  │ 端口：9204          │                    │  │
│  │  │                     │  │                     │                    │  │
│  │  │ 依赖：MySQL + Python│  └─────────────────────┘                    │  │
│  │  │ 外部：Python 脚本   │                                             │  │
│  │  │ 端口：9203          │                                             │  │
│  │  └─────────────────────┘                                             │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────┐      │  │
│  │  │ opinionflow-rag                                             │      │  │
│  │  │ ──────────────────                                          │      │  │
│  │  │ 向量检索增强服务                                              │      │  │
│  │  │                                                             │      │  │
│  │  │ MilvusNewsImportService + MilvusImportRunner                │      │  │
│  │  │                                                             │      │  │
│  │  │ 功能：新闻向量化导入 Milvus、提供向量检索 API               │      │  │
│  │  │ 依赖：MySQL + Milvus                                        │      │  │
│  │  │ 提供：REST API 供 AI 服务调用                               │      │  │
│  │  │ 端口：9205                                                  │      │  │
│  │  └─────────────────────────────────────────────────────────────┘      │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                          公共模块层                                    │  │
│  │                                                                       │  │
│  │  ┌─────────────────────┐  ┌─────────────────────┐                    │  │
│  │  │ opinionflow-common  │  │ opinionflow-api     │                    │  │
│  │  │ ──────────────────  │  │ ──────────────────  │                    │  │
│  │  │ 公共工具类           │  │ 服务间调用接口定义   │                    │  │
│  │  │                     │  │                     │                    │  │
│  │  │ JacksonConfig       │  │ NewsApi             │                    │  │
│  │  │ WebConfig (CORS)    │  │ AiApi               │                    │  │
│  │  │ 统一响应格式         │  │ RagApi              │                    │  │
│  │  │ 统一异常处理         │  │ SpiderApi           │                    │  │
│  │  │ 工具类               │  │                     │                    │  │
│  │  └─────────────────────┘  └─────────────────────┘                    │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 微服务拆分后的项目目录结构

```
opinionflow-cloud/                          ← 根项目（Gradle 多模块）
├── build.gradle.kts                        ← 根构建文件
├── settings.gradle.kts                     ← 模块注册
│
├── opinionflow-common/                     ← 公共模块（非独立服务）
│   └── src/main/kotlin/
│       └── com/lespider/opinionflow/common/
│           ├── config/
│           │   ├── JacksonConfig.kt
│           │   └── WebConfig.kt
│           ├── core/
│           │   ├── Result.kt              ← 统一响应格式
│           │   ├── PageResult.kt          ← 分页响应
│           │   └── GlobalExceptionHandler.kt
│           └── util/
│               └── DateUtils.kt
│
├── opinionflow-api/                        ← 服务间接口定义（非独立服务）
│   └── src/main/kotlin/
│       └── com/lespider/opinionflow/api/
│           ├── news/
│           │   └── NewsFeignClient.kt     ← 新闻服务 Feign 接口
│           ├── rag/
│           │   └── RagFeignClient.kt      ← RAG 服务 Feign 接口
│           └── dto/
│               ├── NewsSearchRequest.kt
│               └── RagSearchRequest.kt
│
├── opinionflow-gateway/                    ← 网关服务
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/lespider/opinionflow/gateway/
│       │   ├── GatewayApplication.kt
│       │   ├── config/
│       │   │   └── GatewayConfig.kt
│       │   └── filter/
│       │       └── AuthFilter.kt          ← 统一鉴权过滤器
│       └── resources/
│           └── application.yml             ← 路由配置
│
├── opinionflow-news/                       ← 新闻服务
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/lespider/opinionflow/news/
│       │   ├── NewsApplication.kt
│       │   ├── controller/
│       │   │   ├── NewsController.kt
│       │   │   ├── NewYorkNewsController.kt
│       │   │   └── YahooFinanceNewsController.kt
│       │   ├── service/
│       │   │   ├── NewsService.kt
│       │   │   ├── NewYorkNewsService.kt
│       │   │   └── YahooFinanceNewsService.kt
│       │   ├── domain/
│       │   │   ├── WyNews.kt
│       │   │   ├── FalshNews.kt
│       │   │   ├── NewYorkNews.kt
│       │   │   ├── YahooFinanceNews.kt
│       │   │   └── NewsConstants.kt
│       │   └── repo/
│       │       ├── WyNewsRepository.kt
│       │       ├── FalshNewsRepository.kt
│       │       ├── NewYorkNewsRepository.kt
│       │       └── YahooFinanceNewsRepository.kt
│       └── resources/
│           └── application.yml
│
├── opinionflow-ai/                         ← AI 服务
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/lespider/opinionflow/ai/
│       │   ├── AiApplication.kt
│       │   ├── controller/
│       │   │   ├── AiController.kt
│       │   │   └── ChatMemoryController.kt
│       │   ├── service/
│       │   │   ├── AiParseService.kt
│       │   │   └── ChatMemoryService.kt
│       │   ├── domain/
│       │   │   └── ChatHistory.kt
│       │   ├── repo/
│       │   │   └── ChatHistoryRepository.kt
│       │   └── client/
│       │       └── RagFeignClient.kt      ← 调用 RAG 服务
│       └── resources/
│           └── application.yml
│
├── opinionflow-spider/                     ← 爬虫服务
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/lespider/opinionflow/spider/
│       │   ├── SpiderApplication.kt
│       │   ├── controller/
│       │   │   ├── ScriptController.kt
│       │   │   └── StockCommentController.kt
│       │   ├── service/
│       │   │   ├── ScriptRunnerService.kt
│       │   │   └── StockCommentService.kt
│       │   ├── domain/
│       │   │   └── StockComment.kt
│       │   └── repo/
│       │       └── StockCommentRepository.kt
│       └── resources/
│           └── application.yml
│
├── opinionflow-rag/                        ← RAG 向量检索服务
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/lespider/opinionflow/rag/
│       │   ├── RagApplication.kt
│       │   ├── controller/
│       │   │   └── RagController.kt       ← 提供检索 API
│       │   ├── service/
│       │   │   └── MilvusNewsImportService.kt
│       │   ├── config/
│       │   │   └── MilvusConfig.kt
│       │   ├── domain/
│       │   │   └── MilvusImportState.kt
│       │   ├── repo/
│       │   │   └── MilvusImportStateRepository.kt
│       │   └── runner/
│       │       └── MilvusImportRunner.kt
│       └── resources/
│           └── application.yml
│
├── opinionflow-echart/                     ← 图表服务
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/lespider/opinionflow/echart/
│       │   ├── EchartApplication.kt
│       │   ├── controller/
│       │   │   └── EchartController.kt
│       │   └── service/
│       │       └── EchartFileService.kt   ← 文件存储抽象
│       └── resources/
│           └── application.yml
│
└── sql/                                    ← 数据库脚本
    ├── init.sql
    ├── V2__create_chat_history.sql
    └── V3__create_milvus_import_state.sql
```

---

## 四、需要改造的目录和文件清单

### 4.1 改造总览

```
┌─────────────────────────────────────────────────────────────────┐
│                      改造工作量评估                              │
│                                                                 │
│  ┌──────────────────────┬────────┬──────────────────────────┐   │
│  │ 改造项                │ 工作量  │ 说明                     │   │
│  ├──────────────────────┼────────┼──────────────────────────┤   │
│  │ 1. 项目结构重组       │ ★★★☆☆  │ 拆分为 Gradle 多模块     │   │
│  │ 2. 引入 Spring Cloud │ ★★★★☆  │ 新增网关/注册/配置中心   │   │
│  │ 3. 代码迁移           │ ★★☆☆☆  │ 按模块移动现有代码       │   │
│  │ 4. 服务间通信         │ ★★★☆☆  │ Feign/RestTemplate      │   │
│  │ 5. 数据库拆分         │ ★★☆☆☆  │ 按服务拆分数据库/表      │   │
│  │ 6. 配置外部化         │ ★★☆☆☆  │ Nacos 配置中心           │   │
│  │ 7. 前端适配           │ ★★☆☆☆  │ API 路径 + 统一入口      │   │
│  │ 8. Docker 容器化      │ ★★★☆☆  │ 每个服务独立部署         │   │
│  └──────────────────────┴────────┴──────────────────────────┘   │
│                                                                 │
│  总工作量：约 2-3 周（一人开发）                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 详细改造清单

#### 4.2.1 需要新建的文件/目录

```
新建：
├── opinionflow-gateway/                  ← 整个模块新建
│   ├── GatewayApplication.kt
│   ├── application.yml                   ← 路由规则
│   └── filter/AuthFilter.kt
│
├── opinionflow-common/                   ← 整个模块新建
│   ├── Result.kt                         ← 统一响应
│   └── GlobalExceptionHandler.kt
│
├── opinionflow-api/                      ← 整个模块新建
│   └── *FeignClient.kt                   ← 服务间接口
│
└── 每个服务的 application.yml            ← Nacos 注册配置
```

#### 4.2.2 需要移动的现有文件

```
从 src/main/kotlin/com/lespider/opinionflow/ 移动到：

config/JacksonConfig.kt      → opinionflow-common/config/
config/WebConfig.kt           → opinionflow-common/config/

web/NewsController.kt         → opinionflow-news/controller/
web/NewYorkNewsController.kt  → opinionflow-news/controller/
web/YahooFinanceNewsController.kt → opinionflow-news/controller/
service/NewsService.kt        → opinionflow-news/service/
service/NewYorkNewsService.kt → opinionflow-news/service/
service/YahooFinanceNewsService.kt → opinionflow-news/service/
domain/WyNews.kt              → opinionflow-news/domain/
domain/FalshNews.kt           → opinionflow-news/domain/
domain/NewYorkNews.kt         → opinionflow-news/domain/
domain/YahooFinanceNews.kt    → opinionflow-news/domain/
domain/NewsConstants.kt       → opinionflow-news/domain/
repo/WyNewsRepository.kt      → opinionflow-news/repo/
repo/FalshNewsRepository.kt   → opinionflow-news/repo/
repo/NewYorkNewsRepository.kt → opinionflow-news/repo/
repo/YahooFinanceNewsRepository.kt → opinionflow-news/repo/

web/AiController.kt           → opinionflow-ai/controller/
web/ChatMemoryController.kt   → opinionflow-ai/controller/
service/AiParseService.kt     → opinionflow-ai/service/
service/ChatMemoryService.kt  → opinionflow-ai/service/
domain/ChatHistory.kt         → opinionflow-ai/domain/
repository/ChatHistoryRepository.kt → opinionflow-ai/repo/

web/ScriptController.kt       → opinionflow-spider/controller/
web/StockCommentController.kt → opinionflow-spider/controller/
service/ScriptRunnerService.kt → opinionflow-spider/service/
service/StockCommentService.kt → opinionflow-spider/service/
domain/StockComment.kt        → opinionflow-spider/domain/
repo/StockCommentRepository.kt → opinionflow-spider/repo/

service/MilvusNewsImportService.kt → opinionflow-rag/service/
config/MilvusConfig.kt        → opinionflow-rag/config/
domain/MilvusImportState.kt   → opinionflow-rag/domain/
repo/MilvusImportStateRepository.kt → opinionflow-rag/repo/
runner/MilvusImportRunner.kt  → opinionflow-rag/runner/

web/EchartController.kt       → opinionflow-echart/controller/
```

#### 4.2.3 需要修改的现有文件

```
修改：
  OpinionFlowApplication.kt              → 删除（每个服务有自己的启动类）
  build.gradle.kts                       → 转为根项目构建文件
  settings.gradle.kts                    → 注册所有子模块
  application.properties                 → 拆分为多个 application.yml

需要代码改造的文件：
  AiParseService.kt                      → RAG 调用改为 Feign 远程调用
  ChatMemoryService.kt                   → 包名变更
  MilvusNewsImportService.kt             → 提取为独立 RAG 服务的 API
  EchartController.kt                    → 文件路径改为服务内部路径
  ScriptRunnerService.kt                 → Python 脚本路径改为配置化
```

---

## 五、关键改造点详解

### 5.1 构建系统改造（Gradle 多模块）

```kotlin
// settings.gradle.kts（根项目）
rootProject.name = "opinionflow-cloud"

include("opinionflow-common")
include("opinionflow-api")
include("opinionflow-gateway")
include("opinionflow-news")
include("opinionflow-ai")
include("opinionflow-spider")
include("opinionflow-rag")
include("opinionflow-echart")
```

```kotlin
// 根 build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.spring") version "2.1.20" apply false
    id("org.springframework.boot") version "3.4.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    group = "com.lespider.opinionflow"
    version = "0.0.1-SNAPSHOT"
    
    repositories {
        mavenCentral()
    }
}
```

### 5.2 引入 Spring Cloud 依赖

```kotlin
// 每个微服务的 build.gradle.kts 需要新增：
dependencies {
    // Spring Cloud Alibaba
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery")
    implementation("com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config")
    
    // 如果使用 Spring Cloud Gateway
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    
    // 服务间调用
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    
    // 原有的业务依赖保留
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // ... 其他原有依赖
}
```

### 5.3 AI 服务调用 RAG 服务的改造

```
改造前（单体 - 直接调用）：
┌──────────────────────────────────────────────────────┐
│ AiParseService.kt                                    │
│                                                      │
│ @Autowired                                           │
│ private lateinit var milvusNewsImportService: ...    │
│                                                      │
│ fun parse(content: String): String {                 │
│     // 直接调用同一 JVM 中的 RAG 服务                │
│     val results = milvusNewsImportService.search(...)│
│     // 注入到 AI prompt                              │
│ }                                                    │
└──────────────────────────────────────────────────────┘

改造后（微服务 - Feign 远程调用）：
┌──────────────────────────────────────────────────────┐
│ opinionflow-api/RagFeignClient.kt                    │
│                                                      │
│ @FeignClient("opinionflow-rag")                      │
│ interface RagFeignClient {                           │
│     @PostMapping("/internal/rag/search")             │
│     fun search(@RequestBody req: RagSearchRequest)   │
│         : List<RagSearchResult>                      │
│ }                                                    │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│ opinionflow-ai/AiParseService.kt                     │
│                                                      │
│ @Autowired                                           │
│ private lateinit var ragClient: RagFeignClient       │
│                                                      │
│ fun parse(content: String): String {                 │
│     // 通过 Feign 远程调用 RAG 服务                  │
│     val results = ragClient.search(                  │
│         RagSearchRequest(query = content, topK = 5)  │
│     )                                                │
│     // 注入到 AI prompt                              │
│ }                                                    │
└──────────────────────────────────────────────────────┘
```

### 5.4 配置外部化（Nacos）

```
改造前（application.properties 硬编码）：
  spring.datasource.url=jdbc:mysql://127.0.0.1:3306/spider
  spring.data.redis.host=127.0.0.1
  milvus.host=127.0.0.1

改造后（Nacos 配置中心）：
  每个服务的 application.yml 只保留：
    spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848
    spring.cloud.nacos.config.server-addr=127.0.0.1:8848
    spring.application.name=opinionflow-news

  数据库等敏感配置移到 Nacos 配置中心：
    opinionflow-news.yml  → MySQL + Redis 连接信息
    opinionflow-ai.yml    → MySQL + Redis + DeepSeek API Key
    opinionflow-rag.yml   → MySQL + Milvus 连接信息
    opinionflow-spider.yml → MySQL + Python 脚本路径
```

### 5.5 前端改造

```
改造前：
  Vue 前端直接请求 http://localhost:8080/api/...

改造后：
  Vue 前端统一请求 http://gateway:8080/api/...
  
  Gateway 根据路径自动路由到对应微服务：
    /api/news/**       → opinionflow-news:9201
    /api/ai/**         → opinionflow-ai:9202
    /api/chat-memory/** → opinionflow-ai:9202
    /api/comments/**   → opinionflow-spider:9203
    /api/scripts/**    → opinionflow-spider:9203
    /api/echart/**     → opinionflow-echart:9204
    /api/nytimes/**    → opinionflow-news:9201
    /api/yahoo/**      → opinionflow-news:9201

  前端代码改动：
    → API 基础 URL 改为网关地址
    → CORS 配置移至网关层
    → 其他代码基本不用改
```

### 5.6 EchartController 文件存储改造

```
改造前：
  EchartController.kt 直接写文件到 ../opinionflow-vue/public/echart/
  → 微服务化后，每个服务独立部署，相对路径不存在了

改造后（三种方案选一）：
  方案 A：改为服务内部存储 + 提供下载 API
    → 文件存在 opinionflow-echart 服务内部
    → 前端通过 /api/echart/read/{filename} 获取内容

  方案 B：存入 MySQL
    → 新增 echart_json 表
    → 存储 JSON 内容和元数据

  方案 C：存入 OSS/MinIO（推荐）
    → 上传到对象存储
    → 返回 URL 给前端
```

---

## 六、推荐的迁移步骤（分阶段）

### 阶段一：准备阶段（1-2 天）

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Step 1: 搭建基础设施                                           │
│  ─────────────────────                                          │
│  □ 安装 Nacos（注册中心 + 配置中心）                             │
│  □ 确保 MySQL / Redis / Milvus 正常运行                         │
│  □ 准备 Docker 环境（可选）                                      │
│                                                                 │
│  Step 2: 创建多模块项目骨架                                      │
│  ─────────────────────                                          │
│  □ 创建 opinionflow-cloud 根项目                                │
│  □ 创建各子模块目录结构                                          │
│  □ 配置根 build.gradle.kts 和 settings.gradle.kts               │
│  □ 每个子模块配置自己的 build.gradle.kts                        │
│                                                                 │
│  Step 3: 创建公共模块                                            │
│  ─────────────────────                                          │
│  □ opinionflow-common：迁移 JacksonConfig、WebConfig            │
│  □ 统一响应格式 Result<T>                                        │
│  □ 全局异常处理                                                  │
│  □ opinionflow-api：定义 Feign 接口                             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 阶段二：拆分服务（3-5 天）

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Step 4: 先拆最独立的模块（低风险）                               │
│  ─────────────────────────────                                  │
│  □ opinionflow-echart（无外部依赖，最简单）                      │
│  □ opinionflow-spider（独立的爬虫执行）                          │
│  □ 验证：启动服务，测试 API                                      │
│                                                                 │
│  Step 5: 拆数据查询服务                                          │
│  ─────────────────────                                          │
│  □ opinionflow-news（多源新闻查询）                              │
│  □ 验证：分页查询、详情查询正常                                  │
│                                                                 │
│  Step 6: 拆向量检索服务                                          │
│  ─────────────────────                                          │
│  □ opinionflow-rag（Milvus 相关）                               │
│  □ 暴露 REST API 供 AI 服务调用                                 │
│  □ 验证：向量导入和检索正常                                      │
│                                                                 │
│  Step 7: 拆 AI 服务（最复杂）                                    │
│  ─────────────────────                                          │
│  □ opinionflow-ai（AI 解析 + 对话）                             │
│  □ 改造 RAG 调用为 Feign 远程调用                               │
│  □ 验证：AI 对话、流式输出正常                                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 阶段三：网关与集成（2-3 天）

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Step 8: 搭建网关服务                                            │
│  ─────────────────────                                          │
│  □ opinionflow-gateway（Spring Cloud Gateway）                  │
│  □ 配置路由规则                                                  │
│  □ 配置跨域（从 WebConfig 迁移）                                │
│  □ 配置限流/熔断（可选）                                         │
│                                                                 │
│  Step 9: 配置 Nacos                                             │
│  ─────────────────────                                          │
│  □ 所有服务注册到 Nacos                                         │
│  □ 敏感配置迁移到 Nacos 配置中心                                │
│  □ 验证服务发现和配置加载                                        │
│                                                                 │
│  Step 10: 前端适配                                               │
│  ─────────────────────                                          │
│  □ API 基础 URL 改为网关地址                                    │
│  □ 测试所有功能                                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 阶段四：优化与部署（2-3 天）

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Step 11: 容器化部署                                             │
│  ─────────────────────                                          │
│  □ 每个服务编写 Dockerfile                                      │
│  □ 编写 docker-compose.yml                                      │
│  □ 一键启动所有服务                                              │
│                                                                 │
│  Step 12: 优化                                                   │
│  ─────────────────────                                          │
│  □ 添加链路追踪（Skywalking，可选）                             │
│  □ 添加监控告警（Prometheus + Grafana，可选）                   │
│  □ 性能测试和调优                                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 七、技术选型对照表

```
┌──────────────────┬──────────────────────┬──────────────────────────┐
│ 能力              │ 当前方案              │ 微服务方案                │
├──────────────────┼──────────────────────┼──────────────────────────┤
│ 语言/框架         │ Kotlin + Spring Boot │ Kotlin + Spring Boot     │
│                  │ 3.4.5                │ 3.4.5 + Cloud 2023.x    │
├──────────────────┼──────────────────────┼──────────────────────────┤
│ 构建工具          │ Gradle 单模块         │ Gradle 多模块             │
├──────────────────┼──────────────────────┼──────────────────────────┤
│ 服务注册/发现     │ 无                   │ Nacos                    │
├──────────────────┼──────────────────────┼──────────────────────────┤
│ 配置中心          │ application.properties│ Nacos Config             │
│                  │ + key.properties     │                          │
├──────────────────┼──────────────────────┼──────────────────────────┤
│ API 网关          │ 无                   │ Spring Cloud Gateway     │
├──────────────────┼──────────────────────┼──────────────────────────┤
│ 服务间通信        │ 直接方法调用          │ OpenFeign + LoadBalancer │
├──────────────────┼──────────────────────┼──────────────────────────┤
│ 数据库            │ 单库 spider           │ 可按服务拆分库/共享库    │
├──────────────────┼──────────────────────┼──────────────────────────┤
│ 缓存              │ Redis 单实例          │ Redis（共享或独立）       │
├──────────────────┼──────────────────────┼──────────────────────────┤
│ 部署方式          │ 单 JAR               │ 每服务独立 JAR/Docker    │
├──────────────────┼──────────────────────┼──────────────────────────┤
│ 前端              │ 直连后端              │ 通过网关统一入口          │
└──────────────────┴──────────────────────┴──────────────────────────┘
```

---

## 八、风险与注意事项

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  ⚠️ 风险 1：数据库拆分策略                                       │
│  ─────────────────────────────                                  │
│  当前所有表在同一个 spider 库中                                  │
│                                                                 │
│  方案 A（推荐初期）：共享数据库                                  │
│    → 所有微服务连接同一个 spider 库                              │
│    → 优点：迁移简单，不用改 SQL                                 │
│    → 缺点：服务间通过数据库耦合                                  │
│                                                                 │
│  方案 B（后期优化）：按服务拆分数据库                            │
│    → news_db：WyNews、FalshNews、NewYorkNews、YahooFinanceNews │
│    → ai_db：ChatHistory                                        │
│    → spider_db：StockComment                                   │
│    → rag_db：MilvusImportState                                 │
│    → 优点：完全解耦                                             │
│    → 缺点：需要数据迁移，跨库查询困难                           │
│                                                                 │
│  ⚠️ 风险 2：EchartController 文件路径                           │
│  ─────────────────────────────                                  │
│  当前写文件到 ../opinionflow-vue/public/echart/                │
│  微服务化后这个相对路径不存在                                    │
│  → 需要改为数据库存储或对象存储                                  │
│                                                                 │
│  ⚠️ 风险 3：SSE 流式输出                                        │
│  ─────────────────────────────                                  │
│  AiController 和 ChatMemoryController 使用 SSE 流式输出        │
│  → Gateway 对 SSE 支持需要注意配置                              │
│  → 确保 Gateway 不缓冲 SSE 响应                                 │
│                                                                 │
│  ⚠️ 风险 4：Python 爬虫脚本                                     │
│  ─────────────────────────────                                  │
│  ScriptRunnerService 执行本地 Python 脚本                       │
│  → 微服务化后需要确保每个容器都有 Python 环境                   │
│  → 或改为独立的爬虫 Worker 服务                                 │
│                                                                 │
│  ⚠️ 风险 5：Kotlin + Spring Cloud 兼容性                        │
│  ─────────────────────────────                                  │
│  RuoYi-Cloud-Plus 用的是 Java                                   │
│  你的项目用 Kotlin，需要确保 Spring Cloud 组件兼容              │
│  → Spring Cloud 2023.x + Spring Boot 3.4.x 完全支持 Kotlin    │
│  → Nacos 客户端需要 2022.0.0.0+ 版本                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 九、与 RuoYi-Cloud-Plus 的对应关系

```
┌─────────────────────────┬──────────────────────────────────────┐
│ RuoYi-Cloud-Plus 目录    │ OpinionFlow 对应                     │
├─────────────────────────┼──────────────────────────────────────┤
│ ruoyi-gateway           │ opinionflow-gateway                  │
│ ruoyi-auth              │ 不需要（暂无用户体系）                │
│ ruoyi-common            │ opinionflow-common                   │
│ ruoyi-api               │ opinionflow-api                      │
│ ruoyi-modules/system    │ opinionflow-news + opinionflow-ai    │
│                         │ + opinionflow-spider + opinionflow-rag│
│                         │ + opinionflow-echart                  │
│ ruoyi-visual            │ 不需要（可用可不用）                  │
└─────────────────────────┴──────────────────────────────────────┘

你不需要照搬 RuoYi-Cloud-Plus 的所有模块
只借鉴它的架构思想：
  ✅ 网关统一入口
  ✅ Nacos 注册/配置
  ✅ 公共模块抽取
  ✅ 服务间 Feign 调用
  ❌ 不需要 ruoyi-auth（暂无用户登录）
  ❌ 不需要 ruoyi-visual（监控可以后期加）
  ❌ 不需要代码生成器
```

---

## 十、一句话总结

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  你的 OpinionFlow 项目非常适合微服务化：                          │
│                                                                 │
│  ✅ 模块间耦合度低（只有 AI→RAG 一个直接调用）                   │
│  ✅ 业务边界清晰（新闻/AI/爬虫/RAG/图表各自独立）                │
│  ✅ 数据源多样（MySQL + Redis + Milvus + 文件）                  │
│  ✅ 技术栈统一（都是 Kotlin + Spring Boot）                      │
│                                                                 │
│  拆分为 5 个微服务 + 2 个公共模块：                               │
│    opinionflow-gateway  → 网关                                   │
│    opinionflow-news     → 新闻查询                              │
│    opinionflow-ai       → AI 解析 + 对话                        │
│    opinionflow-spider   → 爬虫 + 评论                           │
│    opinionflow-rag      → 向量检索                              │
│    opinionflow-echart   → 图表管理                              │
│    opinionflow-common   → 公共模块                              │
│    opinionflow-api      → 服务间接口                            │
│                                                                 │
│  核心改造点：                                                    │
│    1. Gradle 多模块重组                                         │
│    2. 引入 Nacos + Gateway + Feign                              │
│    3. AI→RAG 改为远程调用                                       │
│    4. Echart 文件存储改为数据库/OSS                             │
│    5. 前端 API 统一走网关                                       │
│                                                                 │
│  预计工作量：2-3 周（一人开发）                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘