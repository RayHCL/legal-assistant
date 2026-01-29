package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新用户信息请求")
public class UpdateUserRequest {
    @Schema(description = "昵称", example = "新昵称")
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickName;
    
    @Schema(description = "个人简介", example = "这是我的新简介")
    @Size(max = 500, message = "个人简介不能超过500个字符")
    private String bio;
    
    @Schema(description = "头像URL", example = "https://example.com/new-avatar.jpg")
    @Size(max = 500, message = "头像URL不能超过500个字符")
    private String avatarUrl;
}
