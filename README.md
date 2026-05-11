# OpinionFlow

**舆情分析与新闻聚合平台 — 后端**

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-purple)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

---

## 项目简介

OpinionFlow 是一个集**新闻聚合、舆情分析、脚本调度**于一体的平台：

- **多源新闻聚合** — 网易新闻、纽约时报、雅虎财经、实时财经快讯
- **AI 智能分析** — 接入 OpenAI 兼容 API（DeepSeek / ChatGPT 等），自动分析新闻情绪、行业风险与机会
- **可视化图表** — 基于 ECharts 生成行业风险/机会指数、情绪饼图等
- **脚本调度引擎** — 集成 Python 爬虫脚本，支持评论爬取、新闻爬取、实时数据采集
- **股吧评论分析** — 对股票评论进行情绪分析、主题提取、叙事一致性评估

---

## 项目结构

```
OpinionFlow/
├── key.properties              # 敏感配置（API Key、脚本路径等，不提交 Git）
├── sql/
│   └── init.sql                # 数据库初始化脚本
├── src/main/
│   ├── kotlin/com/lespider/opinionflow/
│   │   ├── config/             # 配置类（CORS、Jackson）
│   │   ├── domain/             # 实体类（JPA）
│   │   ├── repo/               # 数据访问层
│   │   ├── service/            # 业务逻辑层
│   │   └── web/                # RESTful API 控制器
│   └── resources/
│       └── application.properties  # 通用配置文件（数据库、JPA 等）
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 快速开始

### 环境要求

| 工具 | 版本要求 |
|------|---------|
| JDK | 17+ |
| Kotlin | 2.1.x |
| MySQL | 8.0+ |
| Python（可选） | 3.x（运行爬虫脚本时需要） |

### 1. 克隆项目

```bash
git clone https://github.com/lebigpig/OpinionFlow.git
cd OpinionFlow
```

### 2. 初始化数据库

```bash
mysql -u root -p -e "CREATE DATABASE spider DEFAULT CHARSET utf8mb4;"
mysql -u root -p spider < sql/init.sql
```

### 3. 配置 key.properties（敏感配置）

在项目根目录下创建 `key.properties` 文件（该文件已被 `.gitignore` 忽略，不会提交到 Git）：

```properties
# AI 接口配置
opinionflow.ai.api-url=https://api.deepseek.com
opinionflow.ai.api-key=sk-your-api-key
opinionflow.ai.model=deepseek-v4-flash

# 脚本运行配置（根据你的本地路径修改）
opinionflow.scripts.python=你的Python解释器路径
opinionflow.scripts.comment-path=你的评论爬取脚本路径
opinionflow.scripts.news-path=你的新闻爬取脚本路径（多个用逗号分隔）
opinionflow.scripts.realtime-path=你的实时爬取脚本路径
```

### 4. 配置 application.properties（通用配置）

编辑 `src/main/resources/application.properties`，修改数据库连接信息：

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/spider?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=你的数据库密码
```

### 5. 启动后端

```bash
./gradlew bootRun
```

后端默认启动在 `http://localhost:8080`

---

## 配置详解

### key.properties（敏感配置，不提交 Git）

| 配置项 | 说明 | 示例值 |
|--------|------|--------|
| `opinionflow.ai.api-url` | OpenAI 兼容 API 地址 | `https://api.deepseek.com` |
| `opinionflow.ai.api-key` | API 密钥 | `sk-xxxx` |
| `opinionflow.ai.model` | AI 模型名称 | `deepseek-v4-flash` |
| `opinionflow.scripts.python` | Python 解释器路径 | `D:/python/.venv/Scripts/python.exe` |
| `opinionflow.scripts.comment-path` | 评论爬取脚本路径（逗号分隔） | `D:/scripts/crawl_comments.py` |
| `opinionflow.scripts.news-path` | 新闻爬取脚本路径（逗号分隔） | `D:/scripts/news1.py,D:/scripts/news2.py` |
| `opinionflow.scripts.realtime-path` | 实时爬取脚本路径（逗号分隔） | `D:/scripts/realtime.py` |

### application.properties（通用配置，提交到 Git）

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `spring.datasource.url` | MySQL 连接地址 | `jdbc:mysql://127.0.0.1:3306/spider?...` |
| `spring.datasource.username` | 数据库用户名 | `root` |
| `spring.datasource.password` | 数据库密码 | `123456` |
| `spring.jpa.hibernate.ddl-auto` | Hibernate DDL 策略 | `none` |
| `spring.jpa.show-sql` | 是否显示 SQL | `false` |
| `opinionflow.scripts.enabled` | 是否启用脚本运行 | `true` |
| `server.error.include-message` | 400/500 响应包含 message | `always` |

### CORS 跨域配置

在 `src/main/kotlin/.../config/WebConfig.kt` 中配置允许跨域的前端地址。

### ECharts 图表保存路径

在 `src/main/kotlin/.../web/EchartController.kt` 中配置，如果前端项目不在 `../opinionflow-vue/` 请修改此路径。

---

## 数据库表结构

数据库名：`spider`（字符集：`utf8mb4`）

| 表名 | 说明 | 数据来源 |
|------|------|---------|
| `wynews` | 网易新闻（通用新闻） | 脚本爬取 |
| `falsh_news` | 快讯新闻（财经快讯） | 脚本爬取 |
| `new_york_news` | 纽约时报新闻 | 脚本爬取 |
| `yahoo_finance_news` | 雅虎财经新闻 | 脚本爬取 |
| `stock_comment` | 股票评论分析结果 | AI 分析后写入 |

详细建表语句见 `sql/init.sql`。

---

## API 接口一览

### 新闻接口

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

### 评论分析接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/comments` | 股票评论分析列表 |
| GET | `/api/comments/{id}` | 评论分析详情 |

### AI 分析接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/parse` | AI 分析（非流式） |
| POST | `/api/ai/parse/stream` | AI 分析（SSE 流式） |

### AI 回答保存接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai-answers` | 保存 AI 分析回答 |
| GET | `/api/ai-answers` | 获取 AI 回答列表 |
| GET | `/api/ai-answers/{id}` | 获取 AI 回答详情 |

### 脚本运行接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/scripts/run` | 运行单个脚本 |
| POST | `/api/scripts/run/stream` | 运行单个脚本（SSE 流式） |
| POST | `/api/scripts/run-all` | 并行运行全部脚本 |
| POST | `/api/scripts/run-all/stream` | 并行运行全部脚本（SSE 流式） |

### 其他

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/echart/save` | 保存 ECharts 图表 JSON |

---

## 脚本运行说明

| Key | 说明 | 是否需要股票代码 |
|-----|------|----------------|
| `comments` | 评论爬取 | 需要 |
| `news` | 新闻爬取 | 不需要 |
| `realtime` | 实时爬取 | 不需要 |

每个 key 支持配置多个脚本路径（用英文逗号分隔），按顺序依次执行。

---

## 技术栈

- **Spring Boot 3.4.5** — 应用框架
- **Kotlin 2.1.20** — 编程语言
- **Spring Data JPA** — 数据持久化
- **MySQL 8.0+** — 数据库
- **Gradle** — 构建工具

---

## 注意事项

1. **API 密钥安全**：AI 密钥配置在 `key.properties` 中，已被 `.gitignore` 忽略，请勿手动提交
2. **数据库密码**：请使用强密码，不要使用示例中的 `123456`
3. **脚本路径**：脚本路径指向本地文件系统，不同用户需要在 `key.properties` 中自行配置
4. **前端代理**：开发环境下前端 Vite 自动代理 `/api` 到 `localhost:8080`，生产环境需自行配置 Nginx 等反向代理