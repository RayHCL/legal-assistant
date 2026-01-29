package com.legal.assistant.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String nickname;
    
    @TableField("phone")
    private String phone;
    
    private String avatar;
    
    private String bio;
    
    @TableField("is_enabled")
    private Boolean isEnabled;
    
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;

    
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;
    
    @TableField("last_login_ip")
    private String lastLoginIp;
}
