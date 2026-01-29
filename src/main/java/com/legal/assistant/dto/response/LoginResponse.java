package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录响应")
public class LoginResponse {
    @Schema(description = "访问令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;
    
    @Schema(description = "刷新令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;
    
    @Schema(description = "访问令牌过期时间（秒）", example = "3600")
    private Long expiresIn;
    
    @Schema(description = "用户ID", example = "1")
    private Integer userId;
    
    @Schema(description = "用户名", example = "user_138****5678")
    private String username;
    

    
    @Schema(description = "用户头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Schema(description = "用户昵称", example = "张三")
    private String nickname;
}
