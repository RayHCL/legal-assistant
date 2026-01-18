package com.legal.assistant.dto.request;

import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "问答请求")
public class ChatRequest {
    @Schema(description = "用户提问内容", example = "什么是合同纠纷？", required = true)
    @NotBlank(message = "问题内容不能为空")
    private String question;
    
    @Schema(description = "绑定的文件ID集合（可选，支持多个文件）", example = "[1, 2]")
    private List<Long> fileIds;
    
    @Schema(description = "深度思考模式开关", example = "false")
    private Boolean deepThinking;
    
    @Schema(description = "模型类型：DEEPSEEK_CHAT（对话模型）或 DEEPSEEK_REASONER（推理模型）", example = "DEEPSEEK_CHAT", required = true)
    @NotNull(message = "模型类型不能为空")
    private ModelType modelType;
    
    @Schema(description = "温度参数，范围0.0-2.0，默认0.7。值越大回答越有创造性", example = "0.7")
    private Double temperature;
    
    @Schema(description = "Agent类型：LEGAL_CONSULTATION（法律咨询）、RISK_ASSESSMENT（风险评估）、DISPUTE_FOCUS（争议焦点）、CASE_ANALYSIS（案件分析）", example = "LEGAL_CONSULTATION", required = true)
    @NotNull(message = "Agent类型不能为空")
    private AgentType agentType;
    
    @Schema(description = "知识库ID（可选）", example = "1")
    private Long knowledgeBaseId;
    
    @Schema(description = "会话ID（可选），用于延续对话上下文", example = "1")
    private Long conversationId;
    
    @Schema(description = "自动生成标题（布尔值），首次对话时使用", example = "true")
    private Boolean autoGenerateTitle;
}
