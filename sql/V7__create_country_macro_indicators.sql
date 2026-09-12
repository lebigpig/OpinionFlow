-- V7: 创建国家宏观指标表 country_macro_indicators（世界格局地图点击国家后弹出表单查询）
CREATE TABLE IF NOT EXISTS `country_macro_indicators` (
    `id`                    INT PRIMARY KEY AUTO_INCREMENT,
    `country`               VARCHAR(100)  NOT NULL COMMENT '国家/地区名称，与 map_markers.csv 的 country 字段对应',
    `country_code`          VARCHAR(3)    COMMENT 'ISO 3166-1 alpha-3，如 CHN、USA、AUS',
    `year`                  INT           NOT NULL COMMENT '数据年份',
    `region`                VARCHAR(100)  COMMENT '所属区域，如东亚、西非',

    -- 经济总量
    `gdp_usd`               DECIMAL(20,2) COMMENT 'GDP（现价美元）',
    `gdp_growth_pct`        DECIMAL(10,4) COMMENT 'GDP 实际增长率（%）',

    -- 物价
    `cpi_pct`               DECIMAL(10,4) COMMENT 'CPI 同比涨幅（%）',
    `ppi_pct`               DECIMAL(10,4) COMMENT 'PPI 同比涨幅（%）',
    `inflation_pct`         DECIMAL(10,4) COMMENT '通胀率（%）',

    -- 就业
    `employment_rate_pct`   DECIMAL(10,4) COMMENT '就业率（%）',
    `unemployment_rate_pct` DECIMAL(10,4) COMMENT '失业率（%）',

    -- 汇率
    `exchange_rate_usd`     DECIMAL(20,6) COMMENT '本币兑美元年均汇率（1 美元 = ? 本币）',

    -- 人口
    `population_total`      DECIMAL(20,0) COMMENT '总人口',
    `population_growth_pct` DECIMAL(10,4) COMMENT '人口增长率（%）',
    `median_age`            DECIMAL(6,2)  COMMENT '年龄中位数',
    `urban_population_pct`  DECIMAL(10,4) COMMENT '城镇化率（%）',
    `age_0_14_pct`          DECIMAL(10,4) COMMENT '0–14 岁人口占比（%）',
    `age_15_64_pct`         DECIMAL(10,4) COMMENT '15–64 岁人口占比（%）',
    `age_65_plus_pct`       DECIMAL(10,4) COMMENT '65 岁及以上人口占比（%）',

    -- 贸易
    `exports_goods_usd`     DECIMAL(20,2) COMMENT '货物出口额（美元）',
    `imports_goods_usd`     DECIMAL(20,2) COMMENT '货物进口额（美元）',
    `trade_openness_pct`    DECIMAL(10,4) COMMENT '贸易依存度 =（出口+进口）/GDP × 100',
    `import_export_ratio`   DECIMAL(10,4) COMMENT '进口额 / 出口额，用于判断贸易平衡结构',

    -- 政府负债
    `govt_debt_total`       DECIMAL(20,2) COMMENT '政府债务总额（本币）',
    `govt_debt_gdp_pct`     DECIMAL(10,4) COMMENT '政府债务 / GDP（%）',
    `govt_debt_scope`       VARCHAR(200)  COMMENT '债务口径说明',

    -- 政治制度
    `political_system`      VARCHAR(200)  COMMENT '政治制度描述',
    `regime_type`           VARCHAR(100)  COMMENT '政体类型',

    -- 元数据
    `data_source`           VARCHAR(200)  COMMENT '数据来源',
    `source_url`            VARCHAR(500)  COMMENT '来源链接',
    `notes`                 TEXT          COMMENT '备注（口径差异、估算值等）',
    `created_at`            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY `uk_country_year_source` (`country`, `year`, `data_source`),
    INDEX `idx_cmi_country_code` (`country_code`),
    INDEX `idx_cmi_region` (`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='国家宏观指标表';