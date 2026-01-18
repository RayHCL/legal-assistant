package com.legal.assistant.enums;

import lombok.Getter;

@Getter
public enum ModelType {
    DEEPSEEK_CHAT("deepseek-chat", "DeepSeek对话模型"),
    DEEPSEEK_REASONER("deepseek-reasoner", "DeepSeek推理模型");
    
    private final String code;
    private final String description;
    
    ModelType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
