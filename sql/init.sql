-- ============================================================
-- OpinionFlow 数据库初始化脚本
-- 使用前请先创建数据库：CREATE DATABASE spider DEFAULT CHARSET utf8mb4;
-- 然后执行：mysql -u root -p spider < sql/init.sql
-- ============================================================

-- 1. 网易新闻（通用新闻）
CREATE TABLE IF NOT EXISTS `wynews` (
    `id`       BIGINT        NOT NULL PRIMARY KEY,
    `title`    VARCHAR(1024) DEFAULT NULL COMMENT '新闻标题',
    `content`  LONGTEXT      DEFAULT NULL COMMENT '新闻正文',
    `time`     DATETIME      DEFAULT NULL COMMENT '发布时间',
    INDEX `idx_wynews_time` (`time`),
    INDEX `idx_wynews_title` (`title`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='网易新闻（通用新闻）';

-- 2. 快讯新闻（财经快讯）
CREATE TABLE IF NOT EXISTS `falsh_news` (
    `id`       BIGINT        NOT NULL PRIMARY KEY,
    `send`     VARCHAR(1024) DEFAULT NULL COMMENT '快讯标题/摘要',
    `content`  LONGTEXT      DEFAULT NULL COMMENT '快讯正文',
    INDEX `idx_falsh_news_send` (`send`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快讯新闻（财经快讯）';

-- 3. 纽约时报新闻
CREATE TABLE IF NOT EXISTS `new_york_news` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `title`        VARCHAR(1024) DEFAULT NULL COMMENT '新闻标题',
    `summary`      TEXT          DEFAULT NULL COMMENT '新闻摘要',
    `link`         VARCHAR(2048) DEFAULT NULL COMMENT '原文链接',
    `img`          VARCHAR(2048) DEFAULT NULL COMMENT '图片链接',
    `publish_date` VARCHAR(64)   DEFAULT NULL COMMENT '发布日期',
    INDEX `idx_nytimes_date` (`publish_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='纽约时报新闻';

-- 4. 雅虎财经新闻
CREATE TABLE IF NOT EXISTS `yahoo_finance_news` (
    `id`           VARCHAR(128)  NOT NULL PRIMARY KEY COMMENT '新闻唯一 ID',
    `title`        VARCHAR(1024) DEFAULT NULL COMMENT '新闻标题',
    `summary`      TEXT          DEFAULT NULL COMMENT '新闻摘要',
    `display_time` VARCHAR(64)   DEFAULT NULL COMMENT '显示时间',
    `article_url`  VARCHAR(2048) DEFAULT NULL COMMENT '文章链接',
    `img_url`      VARCHAR(2048) DEFAULT NULL COMMENT '图片链接',
    `fetched_at`   DATETIME      DEFAULT NULL COMMENT '抓取时间',
    INDEX `idx_yahoo_display_time` (`display_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='雅虎财经新闻';

-- 5. 股票评论分析结果
CREATE TABLE IF NOT EXISTS `stock_comment` (
    `id`                      BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `stock_code`              VARCHAR(32)   DEFAULT NULL COMMENT '股票代码',
    `analysis_time`           DATETIME      DEFAULT NULL COMMENT '分析时间',
    `total_comments_analyzed` BIGINT        DEFAULT NULL COMMENT '分析的评论总数',
    `mood`                    VARCHAR(64)   DEFAULT NULL COMMENT '情绪/立场倾向',
    `ivi`                     VARCHAR(64)   DEFAULT NULL COMMENT '信息价值指数',
    `narrative_coherence`     VARCHAR(64)   DEFAULT NULL COMMENT '叙事连贯性',
    `main_themes`             JSON          DEFAULT NULL COMMENT '主要主题（JSON 格式：{"主题名": 权重}）',
    `main_themes_content`     TEXT          DEFAULT NULL COMMENT '主要主题的文字描述',
    `theme_count`             INT           DEFAULT NULL COMMENT '主题数量',
    `info_source_reliance`    VARCHAR(64)   DEFAULT NULL COMMENT '信息来源依赖度',
    INDEX `idx_stock_comment_code` (`stock_code`),
    INDEX `idx_stock_comment_time` (`analysis_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='股票评论分析结果';
