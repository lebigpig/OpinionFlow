-- V6: 创建地图标记表 map_markers（世界格局地图拖放标记表单保存）
CREATE TABLE IF NOT EXISTS `map_markers` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `latitude` DECIMAL(10, 7) NOT NULL,
  `longitude` DECIMAL(10, 7) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `category` VARCHAR(50) NOT NULL,
  `icon_type` VARCHAR(30) NOT NULL,
  `country` VARCHAR(50) DEFAULT NULL,
  `region` VARCHAR(100) DEFAULT NULL,
  `description` TEXT DEFAULT NULL,
  `annual_output` VARCHAR(50) DEFAULT NULL,
  `annual_profit` VARCHAR(50) DEFAULT NULL,
  `operator` VARCHAR(100) DEFAULT NULL,
  `status` TINYINT DEFAULT 1,
  `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` VARCHAR(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_country` (`country`),
  INDEX `idx_category` (`category`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
