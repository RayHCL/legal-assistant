package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建分享请求")
public class CreateShareRequest {
    @Schema(description = "要分享的消息ID列表", required = true)
    @NotNull(message = "消息ID列表不能为空")
    private List<Long> messageIds;
}
