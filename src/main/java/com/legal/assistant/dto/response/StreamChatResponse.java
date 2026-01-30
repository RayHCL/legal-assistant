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

    @Schema(description = "状态：thinking（模型深度思考，仅开启 enableThinking 时有）、reasoning（ReAct 推理步骤）、message（普通回复）、artifact（报告/文件输出）、tool_call（工具调用）、tool_result（工具结果）、completed（完成）、error（错误）", example = "message")
    private String status;

    @Schema(description = "自动生成的标题（仅在首次对话时返回）", example = "关于合同纠纷的咨询")
    private String generatedTitle;

    @Schema(description = "是否完成", example = "false")
    private Boolean finished;

    @Schema(description = "工具调用信息（仅当status为tool_call或tool_result时有值）")
    private ToolCallInfo toolCall;

    // ==================== 工厂方法 ====================

    /**
     * 创建消息响应
     */
    public static StreamChatResponse message(Long messageId, Long conversationId, String content, String status) {
        return new StreamChatResponse(messageId, conversationId, content, status, null, false, null);
    }

    /**
     * 创建工具调用响应
     */
    public static StreamChatResponse toolCall(Long messageId, Long conversationId, ToolCallInfo toolInfo) {
        String status = Boolean.TRUE.equals(toolInfo.getIsToolCall()) ? "tool_call" : "tool_result";
        return new StreamChatResponse(messageId, conversationId, "", status, null, false, toolInfo);
    }

    /**
     * 创建完成响应
     */
    public static StreamChatResponse completed(Long messageId, Long conversationId, String generatedTitle) {
        return new StreamChatResponse(messageId, conversationId, "", "completed", generatedTitle, true, null);
    }

    /**
     * 创建错误响应
     */
    public static StreamChatResponse error(Long messageId, Long conversationId, String errorMessage) {
        return new StreamChatResponse(messageId, conversationId, errorMessage, "error", null, true, null);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallInfo {
        @Schema(description = "工具名称")
        private String toolName;

        @Schema(description = "工具调用参数（JSON格式）")
        private String toolArgs;

        @Schema(description = "工具执行结果（仅tool_result时有值）")
        private String toolResult;

        @Schema(description = "是否为工具调用")
        private Boolean isToolCall;

        @Schema(description = "是否为工具结果")
        private Boolean isToolResult;

        /**
         * 创建工具调用信息
         */
        public static ToolCallInfo ofCall(String toolName, String toolArgs) {
            return new ToolCallInfo(toolName, toolArgs, null, true, false);
        }

        /**
         * 创建工具结果信息
         */
        public static ToolCallInfo ofResult(String toolId, String result) {
            return new ToolCallInfo(toolId, null, result, false, true);
        }
    }
}
