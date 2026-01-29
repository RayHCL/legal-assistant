package com.legal.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("share")
public class Share {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("share_id")
    private String shareId;  // 分享唯一标识
    
    @TableField("user_id")
    private Long userId;  // 分享人ID
    
    @TableField("message_ids")
    private String messageIds;  // 消息ID列表（逗号分隔）
    
    @TableField("view_count")
    private Integer viewCount;
    
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;
    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
