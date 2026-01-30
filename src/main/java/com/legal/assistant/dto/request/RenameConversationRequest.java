package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "重命名会话请求")
public class RenameConversationRequest {

    @NotBlank(message = "新标题不能为空")
    @Schema(description = "新标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "我的法律咨询")
    private String newTitle;
}
