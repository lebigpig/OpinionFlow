-- 创建 chat_history 表，用于永久存储 AI 对话历史
CREATE TABLE IF NOT EXISTS `chat_history` (
    `id`         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `session_id` VARCHAR(255)  NOT NULL COMMENT '会话 ID（前端创建新会话时生成）',
    `role`       VARCHAR(20)   NOT NULL COMMENT '消息角色：user / assistant',
    `content`    MEDIUMTEXT    NOT NULL COMMENT '消息内容',
    `token_count` INT          NOT NULL DEFAULT 0 COMMENT '消息 token 数（预留）',
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_session_created` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话历史记录';