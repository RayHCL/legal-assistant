package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "用户信息响应")
public class UserInfoResponse {
    @Schema(description = "用户ID", example = "1")
    private Long id;
    
    @Schema(description = "昵称", example = "用户8000")
    private String nickname;
    
    @Schema(description = "手机号（脱敏显示）", example = "138****8000")
    private String phone;
    
    @Schema(description = "头像URL", example = "http://example.com/avatar.jpg")
    private String avatar;
    
    @Schema(description = "个人简介", example = "我是一名法律工作者")
    private String bio;
    
    @Schema(description = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime createdAt;
    
    @Schema(description = "最后登录时间", example = "2024-01-17T17:00:00")
    private LocalDateTime lastLoginAt;
}
