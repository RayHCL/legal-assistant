package com.legal.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    private Long userId;
    
    private String title;
    
    @TableField("agent_type")
    private String agentType;
    
    @TableField("model_type")
    private String modelType;
    
    @TableField("is_pinned")
    private Boolean isPinned;
    
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
