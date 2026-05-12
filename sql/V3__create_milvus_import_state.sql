-- Milvus 导入状态跟踪表
CREATE TABLE IF NOT EXISTS `milvus_import_state` (
    `source_name`         VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '数据源名称：wynews / falsh / yahoo',
    `last_imported_id`    BIGINT       NOT NULL DEFAULT 0 COMMENT '最后导入的 MySQL 自增 ID',
    `last_imported_time`  VARCHAR(64)  DEFAULT NULL COMMENT '最后导入的时间戳（yahoo 使用）',
    `updated_at`          VARCHAR(64)  DEFAULT NULL COMMENT '最后更新时间',
    UNIQUE KEY `uk_source` (`source_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Milvus 导入状态跟踪';