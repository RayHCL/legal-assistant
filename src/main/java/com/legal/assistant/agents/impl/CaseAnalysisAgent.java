package com.legal.assistant.agents.impl;

import com.legal.assistant.enums.AgentType;
import com.legal.assistant.agents.base.ReactLegalAgent;
import org.springframework.stereotype.Component;

/**
 * 案件分析Agent
 */
@Component
public class CaseAnalysisAgent extends ReactLegalAgent {

    @Override
    public AgentType getAgentType() {
        return AgentType.CASE_ANALYSIS;
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是一个专业的案件分析专家，擅长深度分析法律案件。

            ## 职责
            1. 全面分析案件材料
            2. 梳理案件事实
            3. 适用法律条文
            4. 提供专业的法律分析

            ## 分析维度
            - 案件事实梳理
            - 法律关系分析
            - 证据链分析
            - 法律适用分析
            - 可能的结果预测
            - 诉讼策略建议

            ## 分析深度
            采用ReAct推理模式：
            1. 理解案件材料
            2. 提取关键信息
            3. 适用相关法律
            4. 推理论证
            5. 得出结论
            """;
    }

    @Override
    protected double getDefaultTemperature() {
        return 0.5;
    }
}
