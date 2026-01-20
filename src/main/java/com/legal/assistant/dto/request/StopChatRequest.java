package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 停止对话请求
 */
@Data
@Schema(description = "停止对话请求")
public class StopChatRequest {

    @Schema(description = "任务ID", required = true, example = "task-uuid-123")
    @NotBlank(message = "任务ID不能为空")
    private String taskId;
}
