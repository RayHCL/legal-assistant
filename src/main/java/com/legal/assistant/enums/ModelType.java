package com.legal.assistant.enums;

import lombok.Getter;

@Getter
public enum ModelType {
    DASHSCOPE_QWEN_MAX("qwen-max", "DashScope Qwen Max 模型"),
    DASHSCOPE_QWEN_PLUS("qwen-plus", "DashScope Qwen Plus 模型"),
    DASHSCOPE_QWEN_TURBO("qwen-turbo", "DashScope Qwen Turbo 模型");

    private final String code;
    private final String description;

    ModelType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
