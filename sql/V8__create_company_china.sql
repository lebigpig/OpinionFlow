-- V8: 中国企业财报库 company_china 建表脚本
-- 对应微服务：opinionflow-company（端口 9206，网关路由 /api/company/**）
-- 说明：独立数据库，与 spider 库分离
-- ============================================================

CREATE DATABASE IF NOT EXISTS `company_china` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE `company_china`;

CREATE TABLE IF NOT EXISTS company (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    company_code    VARCHAR(20)  NOT NULL COMMENT '股票代码，如 600519',
    company_name    VARCHAR(200) NOT NULL COMMENT '公司全称',
    short_name      VARCHAR(100) COMMENT '简称',
    exchange        VARCHAR(20)  COMMENT '交易所：SSE/SZSE/HKEX/NASDAQ',
    industry        VARCHAR(100) COMMENT '所属行业',
    fiscal_year_end VARCHAR(10)  COMMENT '财年结束日，如 12-31',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_company_code (company_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司基础信息表';

CREATE TABLE IF NOT EXISTS financial_report (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    company_id      INT           NOT NULL COMMENT '关联 company.id',
    report_type     VARCHAR(20)   NOT NULL COMMENT '报告类型：annual/quarterly/semiannual',
    fiscal_year     INT           NOT NULL COMMENT '财年，如 2024',
    fiscal_period   VARCHAR(10)   NOT NULL COMMENT '期间：FY/Q1/Q2/Q3/H1',
    report_date     DATE          COMMENT '报告日期',
    publish_date    DATE          COMMENT '披露日期',
    currency        VARCHAR(10)   DEFAULT 'CNY' COMMENT '报告货币',
    unit            VARCHAR(20)   DEFAULT '元' COMMENT '金额单位：元/千元/百万元',
    audit_status    VARCHAR(20)   COMMENT '审计意见：unqualified/qualified/...',
    pdf_file_path   VARCHAR(500)  COMMENT '原始 PDF 存储路径',
    pdf_file_hash   VARCHAR(64)   COMMENT 'PDF 文件哈希，防重复',
    parse_status    VARCHAR(20)   DEFAULT 'pending' COMMENT '解析状态：pending/success/failed',
    parsed_at       TIMESTAMP     NULL COMMENT '解析完成时间',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_company_report (company_id, fiscal_year, fiscal_period),
    INDEX idx_fiscal (fiscal_year, fiscal_period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财报主表';

CREATE TABLE IF NOT EXISTS balance_sheet (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id           INT           NOT NULL COMMENT '关联 financial_report.id',
    item_name           VARCHAR(200)  NOT NULL COMMENT '科目名称',
    item_level          TINYINT       NOT NULL DEFAULT 1 COMMENT '层级：1=大类，2=科目，3=其中项',
    parent_item         VARCHAR(200)  COMMENT '上级科目名称',
    is_total            TINYINT       DEFAULT 0 COMMENT '是否合计行：0否 1是',
    is_sub_item         TINYINT       DEFAULT 0 COMMENT '是否“其中”项：0否 1是',
    unit                VARCHAR(10)   DEFAULT '元' COMMENT '原始单位：元/万元/亿元',
    value_current       DECIMAL(20,2) COMMENT '本期/期末金额（统一存元）',
    value_previous      DECIMAL(20,2) COMMENT '上期/期初金额（统一存元）',
    note_ref            VARCHAR(50)   COMMENT '附注索引，如 五、1',
    sort_order          INT           DEFAULT 0 COMMENT '排序',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report (report_id),
    INDEX idx_parent (parent_item)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产负债表';

CREATE TABLE IF NOT EXISTS income_statement (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id           INT           NOT NULL COMMENT '关联 financial_report.id',
    item_name           VARCHAR(200)  NOT NULL COMMENT '科目名称',
    item_level          TINYINT       NOT NULL DEFAULT 1 COMMENT '层级：1=大类，2=科目，3=其中项',
    parent_item         VARCHAR(200)  COMMENT '上级科目名称',
    is_total            TINYINT       DEFAULT 0 COMMENT '是否合计行：0否 1是',
    is_sub_item         TINYINT       DEFAULT 0 COMMENT '是否“其中”项：0否 1是',
    unit                VARCHAR(10)   DEFAULT '元' COMMENT '原始单位：元/万元/亿元',
    value_current       DECIMAL(20,2) COMMENT '本期金额（统一存元）',
    value_previous      DECIMAL(20,2) COMMENT '上期金额（统一存元）',
    note_ref            VARCHAR(50)   COMMENT '附注索引，如 五、1',
    sort_order          INT           DEFAULT 0 COMMENT '排序',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report (report_id),
    INDEX idx_parent (parent_item)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='利润表';

CREATE TABLE IF NOT EXISTS cash_flow_statement (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id           INT           NOT NULL COMMENT '关联 financial_report.id',
    item_name           VARCHAR(200)  NOT NULL COMMENT '科目名称',
    item_level          TINYINT       NOT NULL DEFAULT 1 COMMENT '层级：1=大类，2=科目，3=其中项',
    parent_item         VARCHAR(200)  COMMENT '上级科目名称',
    is_total            TINYINT       DEFAULT 0 COMMENT '是否合计行：0否 1是',
    is_sub_item         TINYINT       DEFAULT 0 COMMENT '是否“其中”项：0否 1是',
    activity_type       VARCHAR(20)   COMMENT '活动类型：operating/investing/financing',
    unit                VARCHAR(10)   DEFAULT '元' COMMENT '原始单位：元/万元/亿元',
    value_current       DECIMAL(20,2) COMMENT '本期金额（统一存元）',
    value_previous      DECIMAL(20,2) COMMENT '上期金额（统一存元）',
    note_ref            VARCHAR(50)   COMMENT '附注索引，如 五、1',
    sort_order          INT           DEFAULT 0 COMMENT '排序',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report (report_id),
    INDEX idx_activity (activity_type),
    INDEX idx_parent (parent_item)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现金流量表';

CREATE TABLE IF NOT EXISTS financial_indicator_value (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id       INT           NOT NULL COMMENT '关联 financial_report.id',
    indicator_code  VARCHAR(50)   NOT NULL COMMENT '指标编码，如 gross_margin、roe、roa',
    indicator_name  VARCHAR(100)  NOT NULL COMMENT '指标名称，如 毛利率',
    indicator_value DECIMAL(20,4) COMMENT '指标值',
    value_previous  DECIMAL(20,4) COMMENT '上期值',
    yoy_change      DECIMAL(10,4) COMMENT '同比变化（%）',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_report_indicator (report_id, indicator_code),
    INDEX idx_indicator (indicator_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务指标计算结果表';