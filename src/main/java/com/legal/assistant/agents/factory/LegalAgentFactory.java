package com.legal.assistant.agents.factory;

import com.legal.assistant.agents.context.AgentContext;
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
    private InteractiveCoordinatorAgent interactiveCoordinatorAgent;

    @Autowired
    private ReportGenerationAgent reportGenerationAgent;

    @Autowired
    private DisputeFocusAgent disputeFocusAgent;

    @Autowired
    private CaseAnalysisAgent caseAnalysisAgent;

    /**
     * 创建Agent
     */
    public ReActAgent createAgent(AgentType agentType, ModelType modelType, Double temperature, AgentContext agentContext) {
        ReactLegalAgent agent = getAgentImpl(agentType);
        return agent.configure(modelType, temperature,agentContext);
    }

    /**
     * 获取Agent实现
     */
    private ReactLegalAgent getAgentImpl(AgentType agentType) {
        return switch (agentType) {
            case LEGAL_CONSULTATION -> legalConsultationAgent;
            case INTERACTIVE_COORDINATOR -> interactiveCoordinatorAgent;
            case REPORT_GENERATION -> reportGenerationAgent;
            case DISPUTE_FOCUS -> disputeFocusAgent;
            case CASE_ANALYSIS -> caseAnalysisAgent;
        };
    }
}
