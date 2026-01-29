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

    /**
     * 用户反馈：LIKE-点赞，DISLIKE-点踩，null-未反馈或已取消
     */
    private String feedback;

    /**
     * 点踩时的反馈内容（仅当 feedback=DISLIKE 时有值）
     */
    @TableField("feedback_text")
    private String feedbackText;

    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
