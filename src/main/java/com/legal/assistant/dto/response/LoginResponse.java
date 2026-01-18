package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录响应")
public class LoginResponse {
    @Schema(description = "访问Token，用于API认证", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;
    
    @Schema(description = "刷新Token，用于刷新访问Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;
    
    @Schema(description = "用户ID", example = "1")
    private Long userId;
    
    @Schema(description = "用户昵称", example = "用户8000")
    private String nickname;
    
    @Schema(description = "用户头像URL", example = "http://example.com/avatar.jpg")
    private String avatar;
    
    @Schema(description = "Token过期时间（秒）", example = "604800")
    private Long expiresIn;
}
