package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "创建分享响应")
public class ShareResponse {
    @Schema(description = "分享ID", example = "share_abc123def456")
    private String shareId;
}
