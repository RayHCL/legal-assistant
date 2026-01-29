package com.legal.assistant.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "Agent类型枚举", example = "LEGAL_CONSULTATION")
public enum AgentType {
    @Schema(description = "普通法律咨询")
    LEGAL_CONSULTATION("普通法律咨询"),

    @Schema(description = "风险评估交互协调器")
    INTERACTIVE_COORDINATOR("风险评估交互协调器"),

    @Schema(description = "报告生成器")
    REPORT_GENERATION("报告生成器"),

    @Schema(description = "争议焦点")
    DISPUTE_FOCUS("争议焦点"),

    @Schema(description = "案件分析")
    CASE_ANALYSIS("案件分析");

    private final String description;

    AgentType(String description) {
        this.description = description;
    }
}
