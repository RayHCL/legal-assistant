package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建会话请求")
public class ConversationRequest {
    @Schema(description = "会话标题", example = "我的法律咨询", required = true)
    @NotBlank(message = "会话标题不能为空")
    private String title;
    
    @Schema(description = "Agent类型", example = "legal_consultation")
    private String agentType;
    
    @Schema(description = "模型类型", example = "deepseek-chat")
    private String modelType;
}
