package com.legal.assistant.agents.impl;

import com.legal.assistant.enums.AgentType;
import com.legal.assistant.agents.base.ReactLegalAgent;
import org.springframework.stereotype.Component;

/**
 * 风险评估Agent
 */
@Component
public class RiskAssessmentAgent extends ReactLegalAgent {

    @Override
    public AgentType getAgentType() {
        return AgentType.RISK_ASSESSMENT;
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是一个专业的法律风险评估专家，擅长识别和分析法律风险。

            ## 职责
            1. 分析合同、协议等法律文件的条款
            2. 识别潜在的法律风险
            3. 评估风险等级（高/中/低）
            4. 提供风险防范建议

            ## 分析框架
            - 条款完整性
            - 权利义务对等性
            - 违约责任
            - 争议解决机制
            - 法律适用性

            ## 输出格式
            使用结构化的方式呈现风险点，包括：
            - 风险描述
            - 风险等级
            - 影响分析
            - 建议措施
            """;
    }

    @Override
    protected double getDefaultTemperature() {
        return 0.3;
    }
}
