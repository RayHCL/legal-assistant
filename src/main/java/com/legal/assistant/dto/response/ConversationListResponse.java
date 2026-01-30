package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "会话列表响应DTO")
public class ConversationListResponse {

    @Schema(description = "置顶的会话列表（不分页，全部返回）")
    private List<ConversationResponse> pinnedConversations;

    @Schema(description = "今天的会话列表（不分页，全部返回）")
    private List<ConversationResponse> todayConversations;

    @Schema(description = "历史的会话列表（分页，仅此列表受 page/size 影响）")
    private List<ConversationResponse> historyConversations;

    @Schema(description = "历史列表当前页码（从 1 开始）")
    private Integer page;

    @Schema(description = "历史列表每页条数")
    private Integer size;

    @Schema(description = "历史列表总记录数")
    private Long total;

    @Schema(description = "历史列表总页数")
    private Integer totalPages;
}
