package com.legal.assistant.agents.impl;

import com.legal.assistant.enums.AgentType;
import com.legal.assistant.agents.base.ReactLegalAgent;
import org.springframework.stereotype.Component;

/**
 * 普通法律咨询Agent
 */
@Component
public class LegalConsultationAgent extends ReactLegalAgent {

    @Override
    public AgentType getAgentType() {
        return AgentType.LEGAL_CONSULTATION;
    }

    @Override
    public String getSystemPrompt() {
        return """
            你是一个专业的法律咨询助手，专门为用户提供法律咨询和建议。

            ## 职责
            1. 理解用户的法律问题
            2. 提供准确的法律解释
            3. 给出合理的建议和参考
            4. 必要时建议用户咨询专业律师

            ## 要求
            - 回答准确、专业、易懂
            - 引用相关法律条文
            - 不提供具体的法律代理服务
            - 对于复杂问题，建议寻求专业法律帮助
            """;
    }

    @Override
    protected double getDefaultTemperature() {
        return 0.7;
    }
}
