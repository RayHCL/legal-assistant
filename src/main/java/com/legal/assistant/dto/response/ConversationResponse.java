package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "会话响应")
public class ConversationResponse {
    @Schema(description = "会话ID", example = "1")
    private Long id;
    
    @Schema(description = "用户ID", example = "1")
    private Long userId;
    
    @Schema(description = "会话标题", example = "我的法律咨询")
    private String title;
    
    @Schema(description = "Agent类型", example = "legal_consultation")
    private String agentType;
    
    @Schema(description = "模型类型", example = "deepseek-chat")
    private String modelType;
    
    @Schema(description = "是否置顶", example = "false")
    private Boolean isPinned;
    
    @Schema(description = "创建时间（时间戳，毫秒）", example = "1704067200000")
    private Long createdAt;
    
    @Schema(description = "更新时间（时间戳，毫秒）", example = "1705507200000")
    private Long updatedAt;
    
    @Schema(description = "消息数量", example = "10")
    private Integer messageCount;
}
