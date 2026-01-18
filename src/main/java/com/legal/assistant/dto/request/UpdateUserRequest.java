package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "更新用户信息请求")
public class UpdateUserRequest {
    @Schema(description = "昵称（2-20个字符）", example = "张三")
    @Size(min = 2, max = 20, message = "昵称长度必须在2-20个字符之间")
    private String nickname;
    
    @Schema(description = "头像URL", example = "http://example.com/avatar.jpg")
    private String avatar;
    
    @Schema(description = "个人简介（最多500个字符）", example = "我是一名法律工作者")
    @Size(max = 500, message = "个人简介不能超过500个字符")
    private String bio;
}
