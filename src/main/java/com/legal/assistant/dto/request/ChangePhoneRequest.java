package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "手机号变更请求")
public class ChangePhoneRequest {

    @NotBlank(message = "新手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "新手机号码格式不正确")
    @Schema(description = "新手机号", example = "13900139000")
    private String newPhoneNumber;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "验证码", example = "123456")
    private String verificationCode;
}
