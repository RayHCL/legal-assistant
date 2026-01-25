package com.legal.assistant.enums;

import lombok.Getter;

@Getter
public enum AgentType {
    LEGAL_CONSULTATION("legal_consultation", "普通法律咨询"),
    INTERACTIVE_COORDINATOR("interactive_coordinator", "交互协调器"),
    REPORT_GENERATION("report_generation", "报告生成器"),
    DISPUTE_FOCUS("dispute_focus", "争议焦点"),
    CASE_ANALYSIS("case_analysis", "案件分析");

    private final String code;
    private final String description;

    AgentType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
