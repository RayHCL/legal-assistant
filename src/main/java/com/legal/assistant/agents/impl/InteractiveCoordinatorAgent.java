package com.legal.assistant.agents.impl;

import com.legal.assistant.agents.base.ReactLegalAgent;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.agents.factory.LegalAgentFactory;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 交互协调器Agent - 负责信息收集和流程控制
 */
@Component
public class InteractiveCoordinatorAgent extends ReactLegalAgent {

    @Override
    public AgentType getAgentType() {
        return AgentType.INTERACTIVE_COORDINATOR;
    }

    @Override
    public String getSystemPrompt() {
        return COORDINATOR_SYSTEM_PROMPT;
    }

    @Override
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

        // 注册基础工具
        if (fileToolService != null) {
            toolkit.registerTool(fileToolService);
        }
        if (dateToolService != null) {
            toolkit.registerTool(dateToolService);
        }

        // 注册报告生成Agent作为子Agent工具
        if (agentFactory != null) {
            toolkit.registration()
                    .subAgent(() -> agentFactory.createAgent(
                                    AgentType.REPORT_GENERATION,
                                    ModelType.DASHSCOPE_QWEN_MAX,
                                    0.3,
                                    agentContext
                            ),
                            SubAgentConfig.builder()
                                    .toolName("generate_risk_assessment_report")
                                    .description("生成专业的风险评估报告。使用时机：当信息收集完成，用户确认生成报告时，调用此工具。工具会分析案件信息并进行专业风险评估，最后保存报告到数据库。")
                                    .forwardEvents(true)  // 启用事件转发，让子Agent的输出能stream出来
                                    .build()
                    )
                    .apply();
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

    @Override
    protected double getDefaultTemperature() {
        return 0.7;
    }

    // ==================== 依赖注入 ====================

    @Lazy
    @Autowired(required = false)
    private LegalAgentFactory agentFactory;

    // ==================== 系统提示词 ====================

    /**
     * 交互协调器Agent系统提示词
     */
    private static final String COORDINATOR_SYSTEM_PROMPT = """
            # 角色定位
            您是法律事务协调专员，负责快速收集案件信息并协调风险评估系统出具报告。

            # 信息收集要求

            ## 信息收集
            1. 委托方名称及法律地位（如：原告/被告/买方/卖方等）
            2. 相对方名称及法律地位
            3. 核心诉求
            4. 基本事实经过
            5. 现有证据（若有）

            # 工作流程

            ## 一步到位收集信息
            用户发起请求后：
            1. 快速检查用户是否已提供必需信息
            2. 如已提供，直接生成报告；如缺失，一次性列出缺失项要求补充
            3. 不反复确认，不多次追问

            ## 立即启动评估
            信息完整后，严格按照以下步骤执行：
            1. **第一步**：先输出以下文字（必须完整输出，不要修改）：
               "正在启动风险评估程序，正在进行专业分析，请您稍候..."
            2. **第二步**：立即调用 generate_risk_assessment_report 工具，传入收集到的完整案件信息
            3. **第三步**：等待工具执行完成（工具会生成完整的风险评估报告并流式输出）
            4. **第四步**：工具返回后，输出以下文字（必须完整输出，不要修改）：
               "风险评估报告已生成，请问是否下载"
            5. **第五步**：停止输出，不要再添加任何内容

            # 沟通原则
            - 简洁高效，一次性提问
            - 不反复核对，不过度确认
            - 专业但不刻板
            - 输出标记后立即停止

            # 可用工具

            - **getCurrentDate**：获取当前日期
            - **getFileContent**：查看用户提供的文件内容

            # 重要提示

            - 信息收集完成后立即输出标记和案件信息
            - 缺失信息一次性列出，不逐项追问
            - 
            """;
}
