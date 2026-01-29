package com.legal.assistant.dto.request;

import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 对话完成请求
 */
@Data
@Schema(description = "对话完成请求")
public class ChatCompletionRequest {

    @Schema(description = "用户问题", required = true, example = "什么是合同纠纷？")
    @NotBlank(message = "问题不能为空")
    private String question;

    @Schema(description = "文件ID列表", example = "[1, 2, 3]")
    private List<Long> fileIds;

    @Schema(description = "模型类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "DASHSCOPE_QWEN_MAX", implementation = ModelType.class)
    @NotNull(message = "模型类型不能为空")
    private ModelType modelType;

    @Schema(description = "Agent类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "LEGAL_CONSULTATION", implementation = AgentType.class)
    @NotNull(message = "Agent类型不能为空")
    private AgentType agentType;

    @Schema(description = "温度参数 (0.0-2.0)", example = "0.7")
    private Double temperature;

    @Schema(description = "知识库ID列表", example = "[1, 2]")
    private List<Long> knowledgeBaseIds;

    @Schema(description = "会话ID（延续对话）", example = "1")
    private Long conversationId;

    @Schema(description = "是否自动生成标题", example = "true")
    private Boolean autoGenerateTitle;

    @Schema(description = "是否启用深度思考", example = "false")
    private Boolean deepThinking;
}
