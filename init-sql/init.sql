-- 创建数据库(如果不存在)
CREATE DATABASE IF NOT EXISTS `legal_assistant` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `legal_assistant`;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `nickname` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '昵称',
  `phone` VARCHAR(11) NOT NULL COMMENT '手机号',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `bio` VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
  `is_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可用(0:禁用,1:启用)',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除,1:已删除)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_is_deleted` (`is_deleted`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 会话表
CREATE TABLE IF NOT EXISTS `conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `title` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '会话标题',
  `agent_type` VARCHAR(50) DEFAULT NULL COMMENT 'Agent类型',
  `model_type` VARCHAR(50) DEFAULT NULL COMMENT '使用的模型',
  `is_pinned` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶(0:否,1:是)',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除(0:未删除,1:已删除)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` DATETIME DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_deleted` (`is_deleted`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 消息表
CREATE TABLE IF NOT EXISTS `message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
  `role` VARCHAR(20) NOT NULL COMMENT '角色(user/assistant)',
  `content` TEXT COMMENT '消息内容',
  `file_ids` TEXT COMMENT '关联的文件ID(JSON)',
  `parameters` TEXT COMMENT '参数配置(JSON)',
  `status` VARCHAR(20) DEFAULT 'completed' COMMENT '状态(thinking/streaming/completed/error)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- 文件表
CREATE TABLE IF NOT EXISTS `file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `knowledge_base_id` BIGINT DEFAULT NULL COMMENT '知识库ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
  `file_type` VARCHAR(50) DEFAULT NULL COMMENT '文件类型',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小(字节)',
  `minio_path` VARCHAR(500) DEFAULT NULL COMMENT 'MinIO存储路径',
  `markdown_path` VARCHAR(500) DEFAULT NULL COMMENT 'Markdown文件路径',
  `status` VARCHAR(20) DEFAULT 'uploading' COMMENT '处理状态(uploading/processing/completed/failed)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_knowledge_base_id` (`knowledge_base_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件表';

-- 分享表
CREATE TABLE IF NOT EXISTS `share` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
  `share_id` VARCHAR(64) NOT NULL COMMENT '分享唯一标识',
  `password_hash` VARCHAR(64) DEFAULT NULL COMMENT '密码哈希',
  `expiration_time` DATETIME NOT NULL COMMENT '过期时间',
  `view_count` INT NOT NULL DEFAULT 0 COMMENT '查看次数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_id` (`share_id`),
  KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享表';

-- 知识库表
CREATE TABLE IF NOT EXISTS `knowledge_base` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `name` VARCHAR(100) NOT NULL COMMENT '知识库名称',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `file_count` INT NOT NULL DEFAULT 0 COMMENT '文件数量',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';
