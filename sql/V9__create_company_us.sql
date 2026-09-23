-- V9: 美国企业财报库 company_us 建表脚本
-- 对应微服务：opinionflow-company（端口 9206，网关路由 /api/company/us/**）
-- 说明：与 company_china 并列的第二个数据源（Spring 多数据源：company_china + company_us）
-- ============================================================================
-- 美股财务报告数据库 -> MySQL 数据库 company_us
-- 适配 US GAAP / SEC EDGAR 报告体系
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `company_us` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE `company_us`;

-- ---------------------------------------------------------------------------
-- 1. 公司基础信息表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS company (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    ticker          VARCHAR(20)  NOT NULL COMMENT '股票代码，如 AAPL / MSFT',
    company_name    VARCHAR(300) NOT NULL COMMENT '公司全称，如 Apple Inc.',
    short_name      VARCHAR(150) COMMENT '简称',
    exchange        VARCHAR(20)  COMMENT '交易所：NYSE / NASDAQ / AMEX / ARCA',
    sector          VARCHAR(100) COMMENT '行业板块(GICS)，如 Technology',
    industry        VARCHAR(100) COMMENT '细分行业',
    cik             VARCHAR(20)  COMMENT 'SEC CIK 编号，如 0000320193',
    isin            VARCHAR(20)  COMMENT '国际证券识别码',
    fiscal_year_end VARCHAR(10)  COMMENT '财年结束日，如 09-30 / 12-31',
    country         VARCHAR(50)  DEFAULT 'United States' COMMENT '注册国家/地区',
    currency        VARCHAR(10)  DEFAULT 'USD' COMMENT '默认报告货币',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ticker (ticker),
    INDEX idx_cik (cik)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='美股公司基础信息表';

-- ---------------------------------------------------------------------------
-- 2. 财报主表(报告元数据)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS financial_report (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    company_id      INT           NOT NULL COMMENT '关联 company.id',
    report_type     VARCHAR(20)   NOT NULL COMMENT '报告类型：annual / quarterly / semiannual',
    fiscal_year     INT           NOT NULL COMMENT '财年，如 2024',
    fiscal_period   VARCHAR(10)   NOT NULL COMMENT '期间：FY / Q1 / Q2 / Q3 / H1',
    form_type       VARCHAR(20)   COMMENT 'SEC 表单类型：10-K / 10-Q / 8-K / 20-F',
    report_date     DATE          COMMENT '报告期截止日',
    publish_date    DATE          COMMENT '实际披露日期',
    accession_no    VARCHAR(30)   COMMENT 'SEC EDGAR 接入号，如 0000320193-24-000123',
    filing_url      VARCHAR(500)  COMMENT 'SEC EDGAR 原始文件链接',
    currency        VARCHAR(10)   DEFAULT 'USD' COMMENT '报告货币',
    unit            VARCHAR(20)   DEFAULT 'USD' COMMENT '金额单位：USD / thousands / millions',
    scale_factor    INT           DEFAULT 1 COMMENT '单位换算因子(转基础单位), 如 1000/1000000',
    audit_status    VARCHAR(30)   COMMENT '审计意见：unqualified / qualified / adverse / disclaimer',
    parse_status    VARCHAR(20)   DEFAULT 'pending' COMMENT '解析状态：pending / success / failed',
    parsed_at       TIMESTAMP     NULL COMMENT '解析完成时间',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_company_report (company_id, fiscal_year, fiscal_period),
    INDEX idx_fiscal (fiscal_year, fiscal_period),
    INDEX idx_form (form_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='美股财报主表';

-- ---------------------------------------------------------------------------
-- 3. 资产负债表 (Balance Sheet - US GAAP)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS balance_sheet (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id           INT           NOT NULL COMMENT '关联 financial_report.id',
    item_code           VARCHAR(80)   COMMENT '科目编码(US GAAP taxonomy tag)，如 AssetsCurrent',
    item_name           VARCHAR(200)  NOT NULL COMMENT '科目名称(中文)',
    item_name_en        VARCHAR(200)  COMMENT '科目英文名称',
    item_level          TINYINT       NOT NULL DEFAULT 1 COMMENT '层级：1=大类/总计，2=科目，3=其中项',
    parent_item         VARCHAR(200)  COMMENT '上级科目名称',
    is_total            TINYINT       DEFAULT 0 COMMENT '是否合计行：0否 1是',
    is_sub_item         TINYINT       DEFAULT 0 COMMENT '是否"其中"项：0否 1是',
    unit                VARCHAR(10)   DEFAULT 'USD' COMMENT '金额单位',
    value_current       DECIMAL(22,2) COMMENT '本期/期末金额',
    value_previous      DECIMAL(22,2) COMMENT '上期/期初金额',
    sort_order          INT           DEFAULT 0 COMMENT '排序序号',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report (report_id),
    INDEX idx_item_code (item_code),
    INDEX idx_parent (parent_item)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产负债表(US GAAP)';

-- ---------------------------------------------------------------------------
-- 4. 利润表 (Income Statement - US GAAP)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS income_statement (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id           INT           NOT NULL COMMENT '关联 financial_report.id',
    item_code           VARCHAR(80)   COMMENT '科目编码(US GAAP taxonomy tag)，如 Revenues',
    item_name           VARCHAR(200)  NOT NULL COMMENT '科目名称(中文)',
    item_name_en        VARCHAR(200)  COMMENT '科目英文名称',
    item_level          TINYINT       NOT NULL DEFAULT 1 COMMENT '层级：1=大类/总计，2=科目，3=其中项',
    parent_item         VARCHAR(200)  COMMENT '上级科目名称',
    is_total            TINYINT       DEFAULT 0 COMMENT '是否合计行：0否 1是',
    is_sub_item         TINYINT       DEFAULT 0 COMMENT '是否"其中"项：0否 1是',
    unit                VARCHAR(10)   DEFAULT 'USD' COMMENT '金额单位',
    value_current       DECIMAL(22,2) COMMENT '本期金额(累计)',
    value_previous      DECIMAL(22,2) COMMENT '上期金额(累计)',
    sort_order          INT           DEFAULT 0 COMMENT '排序序号',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report (report_id),
    INDEX idx_item_code (item_code),
    INDEX idx_parent (parent_item)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='利润表(US GAAP)';

-- ---------------------------------------------------------------------------
-- 5. 现金流量表 (Cash Flow Statement - US GAAP)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cash_flow_statement (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id           INT           NOT NULL COMMENT '关联 financial_report.id',
    item_code           VARCHAR(80)   COMMENT '科目编码(US GAAP taxonomy tag)',
    item_name           VARCHAR(200)  NOT NULL COMMENT '科目名称(中文)',
    item_name_en        VARCHAR(200)  COMMENT '科目英文名称',
    item_level          TINYINT       NOT NULL DEFAULT 1 COMMENT '层级：1=大类/总计，2=科目，3=其中项',
    parent_item         VARCHAR(200)  COMMENT '上级科目名称',
    is_total            TINYINT       DEFAULT 0 COMMENT '是否合计行：0否 1是',
    is_sub_item         TINYINT       DEFAULT 0 COMMENT '是否"其中"项：0否 1是',
    activity_type       VARCHAR(20)   COMMENT '活动类型：operating / investing / financing',
    unit                VARCHAR(10)   DEFAULT 'USD' COMMENT '金额单位',
    value_current       DECIMAL(22,2) COMMENT '本期金额(累计)',
    value_previous      DECIMAL(22,2) COMMENT '上期金额(累计)',
    sort_order          INT           DEFAULT 0 COMMENT '排序序号',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_report (report_id),
    INDEX idx_activity (activity_type),
    INDEX idx_item_code (item_code),
    INDEX idx_parent (parent_item)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='现金流量表(US GAAP)';

-- ---------------------------------------------------------------------------
-- 6. 财务指标表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS financial_indicator (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id       INT           NOT NULL COMMENT '关联 financial_report.id',
    indicator_code  VARCHAR(50)   NOT NULL COMMENT '指标编码，如 gross_margin / roe / eps_diluted',
    indicator_name  VARCHAR(100)  NOT NULL COMMENT '指标名称(中文)',
    indicator_name_en VARCHAR(150) COMMENT '指标英文名称',
    indicator_value DECIMAL(22,6) COMMENT '指标值',
    value_previous  DECIMAL(22,6) COMMENT '上期值',
    yoy_change      DECIMAL(12,4) COMMENT '同比变化(%)',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_report_indicator (report_id, indicator_code),
    INDEX idx_indicator (indicator_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务指标计算结果表';

-- ---------------------------------------------------------------------------
-- 7. SEC Filing 原始文件记录表(可选, 用于存储 10-K/10-Q XBRL 等原始数据)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sec_filing (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id      INT           NOT NULL COMMENT '关联 company.id',
    report_id       INT           COMMENT '关联 financial_report.id(可为空, 未解析时)',
    form_type       VARCHAR(20)   NOT NULL COMMENT '表单类型：10-K / 10-Q / 8-K / DEF 14A / 20-F',
    accession_no    VARCHAR(30)   NOT NULL COMMENT 'SEC 接入号',
    filing_date     DATE          COMMENT '提交日期',
    period_end_date DATE          COMMENT '报告期截止日',
    filing_url      VARCHAR(500)  COMMENT 'EDGAR 文件链接',
    xbrl_url        VARCHAR(500)  COMMENT 'XBRL 实例文档链接',
    raw_json_path   VARCHAR(500)  COMMENT '本地解析后 JSON 存储路径',
    parse_status    VARCHAR(20)   DEFAULT 'pending' COMMENT '解析状态：pending / success / failed',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_accession (accession_no),
    INDEX idx_company (company_id),
    INDEX idx_form_type (form_type),
    INDEX idx_filing_date (filing_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SEC Filing 原始文件记录表';
