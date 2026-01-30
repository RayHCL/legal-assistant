package com.legal.assistant.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "消息反馈请求（点赞/点踩/取消）")
public class MessageFeedbackRequest {

    @NotNull(message = "反馈类型不能为空")
    @Schema(description = "反馈类型：LIKE-点赞，DISLIKE-点踩，NONE-取消反馈", requiredMode = Schema.RequiredMode.REQUIRED, example = "LIKE")
    private FeedbackType feedback;

    @Schema(description = "点踩时的反馈内容（仅当 feedback=DISLIKE 时可填，会写入库）", example = "回答不准确")
    private String feedbackText;

    public enum FeedbackType {
        @Schema(description = "点赞")
        LIKE,
        @Schema(description = "点踩")
        DISLIKE,
        @Schema(description = "取消反馈")
        NONE
    }
}
