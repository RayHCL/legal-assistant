package com.legal.assistant.enums;

import lombok.Getter;

@Getter
public enum AgentType {
    LEGAL_CONSULTATION("legal_consultation", "普通法律咨询"),
    RISK_ASSESSMENT("risk_assessment", "风险评估"),
    INTERACTIVE_RISK_ASSESSMENT("interactive_risk_assessment", "交互式风险评估"),
    DISPUTE_FOCUS("dispute_focus", "争议焦点"),
    CASE_ANALYSIS("case_analysis", "案件分析");
    
    private final String code;
    private final String description;
    
    AgentType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
