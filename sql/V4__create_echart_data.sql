-- V4: 创建 echart_data 表（Echart 图表数据存储）
CREATE TABLE IF NOT EXISTS `echart_data` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `filename` VARCHAR(255) NOT NULL COMMENT '文件名',
    `json_content` LONGTEXT NOT NULL COMMENT 'JSON 内容',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_filename` (`filename`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Echart 图表数据表';