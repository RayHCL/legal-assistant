package com.legal.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("share")
public class Share {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("conversation_id")
    private Long conversationId;
    
    @TableField("share_id")
    private String shareId;  // 分享唯一标识
    
    @TableField("password_hash")
    private String passwordHash;  // 密码哈希
    
    @TableField("expiration_time")
    private LocalDateTime expirationTime;
    
    @TableField("view_count")
    private Integer viewCount;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
