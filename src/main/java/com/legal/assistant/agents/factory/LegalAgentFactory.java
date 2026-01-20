package com.legal.assistant.agents.factory;

import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import com.legal.assistant.agents.base.ReactLegalAgent;
import com.legal.assistant.agents.impl.*;
import io.agentscope.core.ReActAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 法律Agent工厂
 */
@Component
public class LegalAgentFactory {

    @Autowired
    private LegalConsultationAgent legalConsultationAgent;

    @Autowired
    private RiskAssessmentAgent riskAssessmentAgent;

    @Autowired
    private DisputeFocusAgent disputeFocusAgent;

    @Autowired
    private CaseAnalysisAgent caseAnalysisAgent;

    /**
     * 创建Agent
     */
    public ReActAgent createAgent(AgentType agentType, ModelType modelType, Double temperature) {
        ReactLegalAgent agent = getAgentImpl(agentType);
        return agent.configure(modelType, temperature);
    }

    /**
     * 获取Agent实现
     */
    private ReactLegalAgent getAgentImpl(AgentType agentType) {
        return switch (agentType) {
            case LEGAL_CONSULTATION -> legalConsultationAgent;
            case RISK_ASSESSMENT -> riskAssessmentAgent;
            case DISPUTE_FOCUS -> disputeFocusAgent;
            case CASE_ANALYSIS -> caseAnalysisAgent;
        };
    }
}
