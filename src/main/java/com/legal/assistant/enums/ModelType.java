package com.legal.assistant.enums;

import lombok.Getter;

@Getter
public enum ModelType {
    DASHSCOPE_QWEN_MAX("qwen-max", "DashScope Qwen Max 模型", true),
    DASHSCOPE_QWEN_PLUS("qwen-plus", "DashScope Qwen Plus 模型", false),
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
