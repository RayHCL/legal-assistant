package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "流式问答响应")
public class StreamChatResponse {
    @Schema(description = "消息ID", example = "1")
    private Long messageId;
    
    @Schema(description = "会话ID", example = "1")
    private Long conversationId;
    
    @Schema(description = "流式内容片段", example = "根据")
    private String content;
    
    @Schema(description = "状态：thinking（思考中）、streaming（流式输出中）、completed（完成）、error（错误）", example = "streaming")
    private String status;
    
    @Schema(description = "自动生成的标题（仅在首次对话时返回）", example = "关于合同纠纷的咨询")
    private String generatedTitle;
    
    @Schema(description = "是否完成", example = "false")
    private Boolean finished;
}
