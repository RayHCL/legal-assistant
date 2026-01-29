package com.legal.assistant.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "模型类型枚举", example = "QWEN_MAX")
public enum ModelType {
    @Schema(description = "Qwen Max 模型")
    QWEN_MAX("qwen-max", "Qwen Max 模型", true),

    @Schema(description = "DeepSeek Chat 模型")
    DEEPSEEK_CHAT("deepseek-chat", "DeepSeek Chat 模型", false),

    @Schema(description = "DeepSeek R1 模型，支持深度推理")
    DEEPSEEK_R1("deepseek-r1", "DeepSeek R1 模型", true);

    private final String code;
    private final String description;
    private final boolean supportsThinking;

    ModelType(String code, String description, boolean supportsThinking) {
        this.code = code;
        this.description = description;
        this.supportsThinking = supportsThinking;
    }
}
