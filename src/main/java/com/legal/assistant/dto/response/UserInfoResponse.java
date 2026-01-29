package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户信息响应")
public class UserInfoResponse {
    @Schema(description = "用户ID", example = "1")
    private Integer id;
    
    @Schema(description = "手机号码（脱敏）", example = "138****5678")
    private String phoneNumber;
    
    @Schema(description = "昵称", example = "张三")
    private String nickName;
    
    @Schema(description = "个人简介", example = "这是我的个人简介")
    private String bio;
    
    @Schema(description = "头像地址", example = "https://example.com/avatar.jpg")
    private String avatarUrl;
    

}
