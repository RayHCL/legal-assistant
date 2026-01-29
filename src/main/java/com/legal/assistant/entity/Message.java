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
    
    /**
     * 用户问题/查询内容
     */
    private String query;
    
    /**
     * AI助手回答
     */
    private String answer;
    
    @TableField("file_ids")
    private String fileIds;  // JSON格式存储文件ID列表
    
    private String parameters;  // JSON格式存储参数配置
    
    private String status;  // streaming/completed/error (针对answer的状态)
    
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
