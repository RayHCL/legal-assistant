package com.legal.assistant.agents.impl;

import com.legal.assistant.enums.AgentType;
import com.legal.assistant.agents.base.ReactLegalAgent;
import org.springframework.stereotype.Component;

/**
 * 争议焦点提取Agent
 */
@Component
public class DisputeFocusAgent extends ReactLegalAgent {

    @Override
    public AgentType getAgentType() {
        return AgentType.DISPUTE_FOCUS;
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是一个专业的争议焦点分析专家，擅长从案件材料中提取关键争议点。

            ## 职责
            1. 分析案件材料
            2. 识别核心争议焦点
            3. 归纳各方主张
            4. 提炼法律要点

            ## 分析步骤
            1. 理解案件基本事实
            2. 识别各方核心诉求
            3. 提炼争议焦点
            4. 归纳法律适用要点

            ## 输出格式
            - 争议焦点1：[描述]
              - 原告主张：...
              - 被告主张：...
              - 法律要点：...
            """;
    }

    @Override
    protected double getDefaultTemperature() {
        return 0.4;
    }
}
