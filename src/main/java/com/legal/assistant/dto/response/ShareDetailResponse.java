package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "分享详情响应")
public class ShareDetailResponse {
    @Schema(description = "分享ID", example = "share_abc123def456")
    private String shareId;
    
    @Schema(description = "分享人", example = "user_1")
    private String createdBy;
    
    @Schema(description = "创建时间（时间戳，毫秒）", example = "1706422200000")
    private Long createdAt;
    
    @Schema(description = "消息集合")
    private List<MessageDTO> messages;
    
    @Data
    @Schema(description = "消息DTO")
    public static class MessageDTO {
        @Schema(description = "消息ID", example = "1")
        private Long id;
        
        @Schema(description = "查询内容（用户问题）", example = "什么是合同违约？")
        private String query;
        
        @Schema(description = "回答内容（AI回复）", example = "合同违约是指合同当事人一方或双方不履行或不完全履行合同约定的义务...")
        private String answer;
    }
}
