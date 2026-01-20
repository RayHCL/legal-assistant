package com.legal.assistant.agents.base;

import com.legal.assistant.agents.tools.DateToolService;
import com.legal.assistant.agents.tools.FileToolService;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * ReAct法律Agent基类
 */
public abstract class ReactLegalAgent {

    @Value("${ai.dashscope.api-key}")
    protected String apiKey;

    @Value("${agent.legal-consultation.max-iterations:5}")
    protected int maxIterations;

    @Autowired(required = false)
    private FileToolService fileToolService;

    @Autowired(required = false)
    private DateToolService dateToolService;

    /**
     * 获取Agent类型
     */
    public abstract AgentType getAgentType();

    /**
     * 获取系统提示词
     */
    public abstract String getSystemPrompt();

    /**
     * 配置并创建Agent
     */
    public ReActAgent configure(ModelType modelType, Double temperature) {
        String modelName = modelType.getCode();

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();

        // 创建工具集并注册工具
        Toolkit toolkit = new Toolkit();
        if (fileToolService != null) {
            toolkit.registerTool(fileToolService);
        }
        if (dateToolService != null) {
            toolkit.registerTool(dateToolService);
        }

        return ReActAgent.builder()
                .name(getAgentType().getCode())
                .sysPrompt(getSystemPrompt())
                .model(model)
                .maxIters(maxIterations)
                .toolkit(toolkit)
                .build();
    }

    /**
     * 获取默认温度
     */
    protected double getDefaultTemperature() {
        return 0.1;
    }
}
