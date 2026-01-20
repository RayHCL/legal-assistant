-- ============================================
-- 法律咨询助手系统 - 数据库表结构更新
-- 日期: 2025-01-19
-- 说明:
--   1. 将 file 表重命名为 document_file（避免与 java.io.File 冲突）
--   2. 新增 vector_chunk 表用于存储向量分块信息
-- ============================================

-- ============================================
-- 1. 重命名 file 表为 document_file
-- ============================================


-- ============================================
-- 2. 创建向量分块表
-- ============================================
CREATE TABLE IF NOT EXISTS `vector_chunk` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `knowledge_base_id` BIGINT NOT NULL COMMENT '知识库ID',
    `file_id` BIGINT NOT NULL COMMENT '文件ID',
    `chunk_index` INT NOT NULL COMMENT '分块索引',
    `content` TEXT NOT NULL COMMENT '文本内容',
    `vector_id` VARCHAR(255) DEFAULT NULL COMMENT 'Milvus向量ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_knowledge_base_id` (`knowledge_base_id`),
    KEY `idx_file_id` (`file_id`),
    KEY `idx_vector_id` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='向量分块表';

-- ============================================
-- 3. 添加外键约束（可选）
-- ============================================
-- 向量分块表关联知识库表
ALTER TABLE `vector_chunk`
ADD CONSTRAINT `fk_vector_chunk_knowledge_base`
FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_base`(`id`)
ON DELETE CASCADE ON UPDATE CASCADE;

-- 向量分块表关联文档文件表
ALTER TABLE `vector_chunk`
ADD CONSTRAINT `fk_vector_chunk_file`
FOREIGN KEY (`file_id`) REFERENCES `document_file`(`id`)
ON DELETE CASCADE ON UPDATE CASCADE;

-- ============================================
-- 4. 更新 application.yml 中的表名配置（如果使用了 MyBatis 配置）
-- ============================================
-- 需要手动更新 application.yml 或 MyBatis 配置中的表名映射

-- ============================================
-- 验证表结构
-- ============================================
-- 查看文档文件表结构


-- 查看向量分块表结构
DESC `vector_chunk`;

-- 查看外键约束
SELECT
    CONSTRAINT_NAME,
    TABLE_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM
    INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE
    TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME IN ('document_file', 'vector_chunk')
    AND CONSTRAINT_NAME LIKE 'fk_%';
