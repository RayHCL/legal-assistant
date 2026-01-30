-- 消息表：将 file_ids 改为 files，存储关联文件的 JSON 数组（对象数组：id, fileName, fileType, fileSize, fileUrl）
-- 若已有 file_ids 列，先添加 files 再删除 file_ids；若为新表则只添加 files

ALTER TABLE message ADD COLUMN files TEXT DEFAULT NULL COMMENT '关联文件 JSON 数组，元素为 { id, fileName, fileType, fileSize, fileUrl }';

-- 若有旧列 file_ids 可删除（按需执行）
-- ALTER TABLE message DROP COLUMN file_ids;
