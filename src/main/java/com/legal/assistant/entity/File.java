package com.legal.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("file")
public class File {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    private Long userId;
    
    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;
    
    @TableField("file_name")
    private String fileName;
    
    @TableField("file_type")
    private String fileType;
    
    @TableField("file_size")
    private Long fileSize;
    
    @TableField("minio_path")
    private String minioPath;
    
    @TableField("markdown_path")
    private String markdownPath;
    
    private String status;  // uploading/processing/completed/failed
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
