package com.legal.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("conversation_id")
    private Long conversationId;
    
    private String role;  // user/assistant
    
    private String content;
    
    @TableField("file_ids")
    private String fileIds;  // JSON格式存储文件ID列表
    
    private String parameters;  // JSON格式存储参数配置
    
    private String status;  // thinking/streaming/completed/error
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
