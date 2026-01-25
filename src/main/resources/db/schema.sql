-- ================================================
-- 法律咨询助手数据库创建脚本
-- 数据库：legal_assistant
-- ================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `legal_assistant` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `legal_assistant`;

-- ================================================
-- 1. 用户表
-- ================================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `nickname` VARCHAR(100) NOT NULL COMMENT '用户昵称',
    `phone` VARCHAR(20) COMMENT '手机号',
    `avatar` VARCHAR(500) COMMENT '头像URL',
    `bio` VARCHAR(500) COMMENT '个人简介',
    `is_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `last_login_at` DATETIME COMMENT '最后登录时间',
    `last_login_ip` VARCHAR(50) COMMENT '最后登录IP',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_last_login_at` (`last_login_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ================================================
-- 2. 会话表
-- ================================================
CREATE TABLE IF NOT EXISTS `conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '会话标题',
    `agent_type` VARCHAR(50) NOT NULL COMMENT '智能体类型',
    `model_type` VARCHAR(50) NOT NULL COMMENT '模型类型',
    `is_pinned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶：0-否，1-是',
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at` DATETIME COMMENT '删除时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_updated_at` (`updated_at`),
    KEY `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- ================================================
-- 3. 消息表
-- ================================================
CREATE TABLE IF NOT EXISTS `message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
    `role` VARCHAR(20) NOT NULL COMMENT '角色：user/assistant',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `file_ids` VARCHAR(1000) COMMENT '文件ID列表（JSON格式）',
    `parameters` TEXT COMMENT '参数配置（JSON格式）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'completed' COMMENT '状态：thinking/streaming/completed/error',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- ================================================
-- 4. 知识库表
-- ================================================
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `name` VARCHAR(200) NOT NULL COMMENT '知识库名称',
    `description` TEXT COMMENT '知识库描述',
    `file_count` INT NOT NULL DEFAULT 0 COMMENT '文件数量',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- ================================================
-- 5. 文档文件表
-- ================================================
CREATE TABLE IF NOT EXISTS `document_file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `knowledge_base_id` BIGINT NOT NULL COMMENT '知识库ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
    `file_type` VARCHAR(50) NOT NULL COMMENT '文件类型',
    `file_size` BIGINT NOT NULL COMMENT '文件大小（字节）',
    `minio_path` VARCHAR(500) NOT NULL COMMENT 'MinIO存储路径',
    `markdown_path` VARCHAR(500) COMMENT 'Markdown文件路径',
    `status` VARCHAR(20) NOT NULL DEFAULT 'processing' COMMENT '状态：processing/completed/failed',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_knowledge_base_id` (`knowledge_base_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档文件表';

-- ================================================
-- 6. 风险评估报告表
-- ================================================
CREATE TABLE IF NOT EXISTS `risk_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `report_id` VARCHAR(50) NOT NULL COMMENT '报告编号，如：RPT20260122001',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
    `our_side` VARCHAR(200) NOT NULL COMMENT '我方当事人',
    `our_identity` VARCHAR(50) NOT NULL COMMENT '我方身份（原告/被告/申请人/被申请人/债权人/债务人等）',
    `other_party` VARCHAR(200) NOT NULL COMMENT '对方当事人',
    `other_identity` VARCHAR(50) NOT NULL COMMENT '对方身份',
    `case_reason` VARCHAR(100) NOT NULL COMMENT '案由',
    `core_demand` TEXT NOT NULL COMMENT '核心诉求',
    `basic_facts` TEXT NOT NULL COMMENT '基本事实',
    `available_core_evidence` TEXT NOT NULL COMMENT '现有核心证据',
    `report_date` VARCHAR(50) NOT NULL COMMENT '报告日期，格式：YYYY年MM月DD日',
    `overall_risk_level` VARCHAR(50) NOT NULL COMMENT '综合风险等级（较低风险/中等风险/较高风险）',
    `overall_risk_score` INT NOT NULL COMMENT '风险评分（10-100分）',
    `overall_risk_score_reason` TEXT COMMENT '风险评分原因',
    `advantages_opportunity_analysis` TEXT COMMENT '优势与机会分析',
    `risk_challenge_alert` TEXT COMMENT '风险挑战提示',
    `risk_point` VARCHAR(500) COMMENT '风险点简述',
    `action_suggestions_subsequent_strategies` TEXT COMMENT '行动建议与后续策略',
    `full_report_content` LONGTEXT COMMENT '完整报告内容（Markdown格式）',
    `minio_path` VARCHAR(500) COMMENT 'MinIO存储路径（PDF文件）',
    `report_file_path` VARCHAR(500) COMMENT '报告文件路径（PDF）- 已废弃',
    `download_link` VARCHAR(500) COMMENT '下载链接',
    `link_expire_time` DATETIME COMMENT '下载链接过期时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_id` (`report_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险评估报告表';

-- ================================================
-- 7. 分享表
-- ================================================
CREATE TABLE IF NOT EXISTS `share` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
    `share_id` VARCHAR(50) NOT NULL COMMENT '分享唯一标识',
    `password_hash` VARCHAR(255) COMMENT '密码哈希',
    `expiration_time` DATETIME COMMENT '过期时间',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '查看次数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_share_id` (`share_id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_expiration_time` (`expiration_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分享表';

-- ================================================
-- 初始化测试数据（可选）
-- ================================================
-- INSERT INTO `user` (`nickname`, `phone`, `is_enabled`) VALUES
-- ('测试用户', '13800138000', 1);
