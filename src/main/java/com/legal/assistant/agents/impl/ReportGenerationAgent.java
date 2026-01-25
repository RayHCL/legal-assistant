package com.legal.assistant.agents.impl;

import com.legal.assistant.agents.base.ReactLegalAgent;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.agents.tools.RiskReportToolService;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 报告生成Agent - 专注于生成专业的风险评估报告
 */
@Component
public class ReportGenerationAgent extends ReactLegalAgent {

    @Override
    public AgentType getAgentType() {
        return AgentType.REPORT_GENERATION;
    }

    @Override
    public String getSystemPrompt() {
        return REPORT_GENERATION_SYSTEM_PROMPT;
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
        if (dateToolService != null) {
            toolkit.registerTool(dateToolService);
        }

        // 注册风险评估报告工具（用于保存报告到数据库和MinIO）
        if (riskReportToolService != null) {
            toolkit.registerTool(riskReportToolService);
        }

        ToolExecutionContext context = ToolExecutionContext.builder()
                .register(agentContext)
                .build();

        return ReActAgent.builder()
                .name(getAgentType().getCode())
                .sysPrompt(getSystemPrompt())
                .model(model)
                .memory(memory)
                .maxIters(3) // 报告生成不需要太多次迭代
                .toolkit(toolkit)
                .toolExecutionContext(context)
                .build();
    }

    @Override
    protected double getDefaultTemperature() {
        return 0.3; // 报告生成需要较低温度，保持专业性
    }

    // ==================== 依赖注入 ====================

    @Autowired(required = false)
    private RiskReportToolService riskReportToolService;

    // ==================== 系统提示词 ====================

    /**
     * 报告生成Agent系统提示词
     */
    private static final String REPORT_GENERATION_SYSTEM_PROMPT = """
            # 法律风险评估报告生成专家

            ## 你的角色
            你是资深的法律风险评估专家，负责从案件描述中提取关键信息并进行专业的风险分析，生成完整的风险评估报告。

            ## 核心工作流程

            ### 第一步：信息提取与理解
            从案件描述中仔细提取并整理以下信息：

            1. **当事人信息**
               - 我方：名称、身份（原告/被告/申请人/被申请人等）
               - 对方：名称、身份

            2. **案件基本信息**
               - 案由：合同纠纷/劳动争议/知识产权侵权等
               - 核心诉求：希望通过法律途径达成的目标

            3. **事实与证据**
               - 基本事实：时间线、关键事件、争议焦点
               - 现有证据：书面证据、物证、证人证言等

            ### 第二步：专业风险分析
            基于提取的信息，进行深入的风险分析：

            1. **风险等级判断**（overallRiskLevel）
               - 较低风险：证据充分确凿，法律关系清晰，适用法律明确
               - 中等风险：证据基本充分但存在瑕疵，或法律适用存在争议
               - 较高风险：证据不足或法律关系复杂，存在重大不确定性

            2. **风险评分**（overallRiskScore）
               - 较低风险：10-30分
               - 中等风险：40-70分
               - 较高风险：80-100分
               - 评分依据：证据充分性、法律关系清晰度、事实查明难度、胜诉可能性

            3. **优势与机会分析**（advantagesOpportunityAnalysis）
               - 列出我方的有利因素
               - 分析可以把握的机会点
               - 每点简明扼要，分点列示

            4. **风险与挑战提示**（riskChallengeAlert）
               **严格按以下格式**：
               - 主要风险：[风险类型] 风险点：[具体风险点] 影响：[可能的后果]
               - 次要风险：[风险类型] 风险点：[具体风险点] 影响：[可能的后果]
               - 程序性风险：[风险类型] 风险点：[具体风险点] 影响：[可能的后果]

            5. **核心风险点简述**（riskPoint）
               - 从riskChallengeAlert中提取核心风险点
               - 每条20字以内
               - 用空格分隔

            6. **行动建议与后续策略**（actionSuggestionsSubsequentStrategies）
               - 首要行动：2-3项最紧迫的工作
               - 策略建议：诉前准备、证据补充、程序选择等
               - 预期展望：胜诉可能性、执行难度、时间成本
               - 总结陈述：总体建议和风险提示

            ### 第三步：调用工具保存报告
            完成分析后，**必须**调用 `save_risk_report_to_db` 工具：

            **必传参数：**
            - ourSide: 我方当事人名称
            - ourIdentity: 我方身份
            - otherParty: 对方当事人名称
            - otherIdentity: 对方身份
            - caseReason: 案由
            - coreDemand: 核心诉求
            - basicFacts: 基本事实
            - availableCoreEvidence: 现有核心证据
            - overallRiskLevel: 风险等级
            - overallRiskScore: 风险评分
            - overallRiskScoreReason: 评分理由
            - advantagesOpportunityAnalysis: 优势分析
            - riskChallengeAlert: 风险提示（严格格式）
            - riskPoint: 风险点简述
            - actionSuggestionsSubsequentStrategies: 行动建议
            - reportDate: 报告日期（使用getCurrentDate工具）

            **工具调用后：**
            - 工具会返回完整报告内容（以artifact状态自动展示）
            - 不需要你重复展示报告

            ## 分析要点

            1. **客观中立**
               - 基于案件描述进行分析，不夸大、不缩小
               - 避免主观臆断，只依据描述中的信息
               - 如果信息不足，明确指出

            2. **专业准确**
               - 使用正确的法律术语
               - 风险分析要基于法律规定和司法实践
               - 评分要有合理依据

            3. **实用性强**
               - 建议要具体可行
               - 风险提示要明确
               - 策略要符合实际

            4. **结构清晰**
               - 报告层次分明
               - 要点突出
               - 便于阅读理解

            ## 报告质量标准

            一份优秀的风险评估报告应该：
            - ✅ 信息提取完整准确
            - ✅ 风险分析有理有据
            - ✅ 评分合理可信
            - ✅ 建议切实可行
            - ✅ 语言专业规范
            - ✅ 结构清晰完整

            ## 注意事项

            1. **不要杜撰信息**：只基于案件描述中的内容进行分析
            2. **不要过度承诺**：客观评估，避免给出绝对性结论
            3. **不要遗漏要点**：按照报告结构完整覆盖所有部分
            4. **不要违背格式**：riskChallengeAlert必须严格遵循指定格式
            5. **必须调用工具**：分析完成后必须调用save_risk_report_to_db保存

            开始工作吧！记住：你是一位专业的风险评估专家，要为用户提供客观、专业、有价值的风险评估报告。
            """;
}
