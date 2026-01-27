package com.legal.assistant.agents.impl;

import com.legal.assistant.agents.base.ReactLegalAgent;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.agents.factory.LegalAgentFactory;
import com.legal.assistant.agents.tools.ReportSaveToolService;
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
    @Autowired(required = false)
    private ReportSaveToolService reportSaveToolService;
    @Autowired(required = false)
    private ReportGenerationAgent reportGenerationAgent;

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
        if (reportSaveToolService != null){
            toolkit.registerTool(reportSaveToolService);
        }

        // 注册报告生成Agent作为子Agent工具
        if (reportGenerationAgent != null) {
            toolkit.registration()
                    .subAgent(() -> reportGenerationAgent.configure(modelType, temperature, agentContext),
                            SubAgentConfig.builder()
                                    .toolName("generate_risk_assessment_report")
                                    .description("【重要工具】生成专业的风险评估报告。当收集到完整的案件信息（委托方、对方、诉求、事实、证据）后，必须调用此工具来生成风险评估报告。工具接收案件描述文本，分析后生成包含风险等级、评分、分析和建议的完整报告。")
                                    .forwardEvents(true)  // 启用事件转发，让子Agent的输出能stream出来
                                    .build()
                    )
                    .apply();
        }

        ToolExecutionContext context = ToolExecutionContext.builder()
                .register(agentContext)
                .build();

        // 获取系统提示词并注入当前时间
        String systemPrompt = injectCurrentTime(getSystemPrompt());

        return ReActAgent.builder()
                .name(getAgentType().getCode())
                .sysPrompt(systemPrompt)
                .model(model)
                .memory(memory)
                .maxIters(10)  // 增加迭代次数，确保有足够的机会调用工具
                .toolkit(toolkit)
                .toolExecutionContext(context)
                .build();
    }

    @Override
    protected double getDefaultTemperature() {
        return 0.7;
    }



    // ==================== 系统提示词 ====================

    /**
     * 交互协调器Agent系统提示词
     */
    private static final String COORDINATOR_SYSTEM_PROMPT = """
            # 角色定位
            您是法律事务协调专员，负责快速收集案件信息并协调风险评估系统出具报告。

            # 当前时间
            {current_time}

            # 信息收集要求

            ## 必需信息
            1. 委托方名称及法律地位（如：原告/被告/买方/卖方等）
            2. 相对方名称及法律地位
            3. 核心诉求
            4. 基本事实经过
            5. 现有证据（若有）

            # 工作流程

            ## 第一步：信息检查
            收到用户请求后，立即检查是否已包含上述5项必需信息。

            ## 第二步：收集缺失信息
            如果信息不完整，一次性列出所有缺失项，要求用户补充。
            示例："为了准确评估风险，我还需要以下信息：1.委托方身份 2.核心诉求金额..."

            ## 第三步：立即启动评估（关键步骤）
            一旦收集到完整信息，**必须**按照以下步骤执行：

            1. **输出启动提示**（必须原样输出）：
               "正在启动风险评估程序，正在进行专业分析，请您稍候..."

            2. **立即调用 generate_risk_assessment_report 工具**：
               - 这是必须的步骤，不能跳过
               - 调用示例：
                 Action: generate_risk_assessment_report
                 Action Input: {"caseInfo": "委托方：张三（原告）；对方：李四面馆（被告）；核心诉求：索赔1000元；基本事实：2024年X月X日在李四面馆就餐发现苍蝇，已取证；现有证据：照片、付款记录"}
               - 将收集到的所有案件信息作为参数传入

            3. **等待工具执行完成**（工具会输出完整的风险评估报告）

            4. **输出完成提示**（必须原样输出）：
               "风险评估报告已生成，请问是否下载"

            # 可用工具

            1. **generate_risk_assessment_report**（最重要的工具）
               - 作用：生成专业的风险评估报告
               - 使用时机：信息收集完成后，必须立即调用
               - 参数格式：案件描述文本（包含委托方、对方、诉求、事实、证据）
               - 重要：这是唯一能生成报告的方式，不能自己生成

            2. **getFileContent**
               - 作用：查看用户提供的文件内容

            3. **generate_download_link**
               - 作用：生成报告下载链接
               - 参数：报告编号

            # 关键原则

            - ✅ 信息完整后必须调用 generate_risk_assessment_report 工具
            - ❌ 不要跳过工具直接生成报告内容
            - ❌ 不要反复追问，一次性列出所有缺失信息
            - ✅ 使用工具后等待工具输出完成
            - ✅ 工具调用是必须的，不是可选项
            """;
}
