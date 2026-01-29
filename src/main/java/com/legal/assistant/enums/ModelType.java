package com.legal.assistant.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "模型类型枚举", example = "DASHSCOPE_QWEN_MAX")
public enum ModelType {
    @Schema(description = "DashScope Qwen Max 模型，支持深度思考")
    DASHSCOPE_QWEN_MAX("qwen-max", "DashScope Qwen Max 模型", true),

    @Schema(description = "DashScope Qwen Plus 模型")
    DASHSCOPE_QWEN_PLUS("qwen-plus", "DashScope Qwen Plus 模型", false),

    @Schema(description = "DashScope Qwen Turbo 模型，响应更快")
    DASHSCOPE_QWEN_TURBO("qwen-turbo", "DashScope Qwen Turbo 模型", false);

    private final String code;
    private final String description;
    private final boolean supportsThinking;

    ModelType(String code, String description, boolean supportsThinking) {
        this.code = code;
        this.description = description;
        this.supportsThinking = supportsThinking;
    }
}
