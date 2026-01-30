package com.legal.assistant.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "建议问题列表响应")
public class SuggestedQuestionsResponse {

    @Schema(description = "建议问题列表", example = "[\"这个问题涉及哪些法律条款？\", \"需要准备哪些证据材料？\"]")
    private List<String> questions;
}
