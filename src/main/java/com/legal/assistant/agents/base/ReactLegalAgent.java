package com.legal.assistant.agents.base;

import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.agents.tools.DateToolService;
import com.legal.assistant.agents.tools.FileToolService;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.ToolExecutionContext;
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

    // 记忆配置
    @Value("${agent.memory.type:AUTO_CONTEXT}")
    protected String memoryType;

    @Value("${agent.memory.msg-threshold:30}")
    protected int msgThreshold;

    @Value("${agent.memory.last-keep:10}")
    protected int lastKeep;

    @Value("${agent.memory.token-ratio:0.3}")
    protected double tokenRatio;

    @Autowired(required = false)
    protected FileToolService fileToolService;

    @Autowired(required = false)
    protected DateToolService dateToolService;

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
    public ReActAgent configure(ModelType modelType, Double temperature, AgentContext agentContext) {
        String modelName = modelType.getCode();

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .defaultOptions(GenerateOptions.builder().temperature(temperature).build())
                .modelName(modelName)
                .build();

        // 创建记忆
        Memory memory = createMemory(model);

        // 创建工具集并注册工具
        Toolkit toolkit = new Toolkit();
        if (fileToolService != null) {
            toolkit.registerTool(fileToolService);
        }
        if (dateToolService != null) {
            toolkit.registerTool(dateToolService);
        }

        ToolExecutionContext context = ToolExecutionContext.builder()
                .register(agentContext)
                .build();
        return ReActAgent.builder()
                .name(getAgentType().getCode())
                .sysPrompt(getSystemPrompt())
                .model(model)
                .memory(memory)
                .maxIters(maxIterations)
                .toolkit(toolkit)
                .toolExecutionContext(context)
                .build();
    }

    /**
     * 创建记忆实例
     */
    protected Memory createMemory(DashScopeChatModel model) {
        if ("AUTO_CONTEXT".equalsIgnoreCase(memoryType)) {
            AutoContextConfig config = AutoContextConfig.builder()
                    .msgThreshold(msgThreshold)
                    .lastKeep(lastKeep)
                    .tokenRatio(tokenRatio)
                    .build();
            return new AutoContextMemory(config, model);
        } else {
            // 默认使用简单内存记忆
            return new InMemoryMemory();
        }
    }

    /**
     * 获取默认温度
     */
    protected double getDefaultTemperature() {
        return 0.1;
    }
}
