package com.legal.assistant.enums;

import lombok.Getter;

@Getter
public enum MessageRole {
    USER("user", "用户"),
    ASSISTANT("assistant", "助手");
    
    private final String code;
    private final String description;
    
    MessageRole(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
