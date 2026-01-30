package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "置顶/取消置顶会话请求")
public class PinConversationRequest {

    @NotNull(message = "是否置顶不能为空")
    @Schema(description = "是否置顶", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean pinned;
}
