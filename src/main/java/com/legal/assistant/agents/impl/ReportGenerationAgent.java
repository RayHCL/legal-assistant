package com.legal.assistant.agents.impl;

import com.legal.assistant.agents.base.ReactLegalAgent;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;

/**
 * 报告生成Agent - 专注于生成专业的风险评估报告
 */
@Component
public class ReportGenerationAgent extends ReactLegalAgent {

    // ==================== 系统提示词 ====================

    /**
     * 报告生成Agent系统提示词
     */
    private static final String REPORT_GENERATION_SYSTEM_PROMPT = """
            # 职责定位
            您是法律风险评估专家系统，负责对案件信息进行专业分析并生成风险评估报告。

            # 当前时间
            {current_time}
            # 分析标准
            ## 案由范围
            合同纠纷(买卖纠纷、租赁纠纷、借贷纠纷、建设工程纠纷)、公司纠纷、知识产权纠纷、金融纠纷、国际贸易纠纷、电子商务纠纷、房地产纠纷

            ## 风险等级与评分
            - **较低（10-30分）**：证据较为充分，法律关系清晰，适用法律明确，胜诉可能性较高
            - **中等（40-70分）**：证据基本充分但存在瑕疵，或法律适用存在争议，需进一步补充或澄清
            - **较高（80-100分）**：证据不足或法律关系复杂，存在重大不确定性，败诉可能性较高

            ## 字段解释与填写规范
            - ourSide：我方当事人名称
            - ourIdentity：我方身份(原告/被告/申请人/被申请人/债权人/债务人/买方/卖方等)
            - otherParty：对方当事人名称
            - otherIdentity：对方身份
            - reportDate：报告日期(使用当前时间的日期，例如2025年12月22日)
            - coreDemand：核心诉求。概括要点，多项诉求需分点列出。诉求不明确时，基于材料合理概括，不得臆测金额
            - basicFacts：基本事实。有时间线按时间顺序: YYYY年MM月DD日:[事实]。无时间线按逻辑顺序，每条单独成行，使用"\\n"换行
            - availableCoreEvidence：现有核心证据，列出所有关键证据，用顿号"、"分隔。无证据写"暂无"
            - advantagesOpportunityAnalysis：优势与机会分析，分点列示
            - riskChallengeAlert：风险与挑战提示，按类型分类，严格按以下格式:
              主要风险:[风险描述] 风险点:[具体风险描述,内容详细] 影响:[可能造成的后果]
              次要风险:[风险描述] 风险点:[具体风险描述,内容详细] 影响:[可能造成的后果]
              程序性风险:[风险描述] 风险点:[具体风险描述,内容详细] 影响:[可能造成的后果]
            - actionSuggestionsSubsequentStrategies：行动建议与后续策略，按以下结构列示:
              - 首要行动：列出2-3项最紧迫工作，并说明法律意义(如:中断诉讼时效、证明权利主张)，按优先级排序
              - 策略建议：财产调查方向(房产、车辆、银行账户等)、诉前谈判措施(律师函等)、诉讼准备方案(含财产保全建议)，采用"并行推进"方式
              - 预期与展望：评估胜诉可能性，指出关键风险点，说明判决执行可行性
              - 总结：综合上述给出结论
            ## 输出完整报告
            严格按照以下模板输出完整报告内容：

            关于"{{ourSide}}与{{otherParty}}{{caseReason}}"案的风险评估报告

            致： {{ourSide}}
            日期： {{reportDate}}
            案由： {{caseReason}}

            一、 报告基础与声明
            本报告基于{{reportDate}}提供的案件所述信息作出。本报告旨在对案件进行初步的、方向性的风险评估，并非对诉讼结果的承诺。随着案件证据的补充和程序的推进，评估结论可能发生变化。本报告为内部法律分析文件，请注意保密。

            二、 案件核心事实梳理
            {{ourIdentity}}：{{ourSide}}
            {{otherIdentity}}：{{otherParty}}
            核心诉求：{{coreDemand}}

            ● 基本事实：
            {{basicFacts}}

            ● 现有核心证据：
            {{availableCoreEvidence}}

            三、 初步风险评估
            综合风险：{{overallRiskScore}}分（{{overallRiskLevel}}风险）

            （一）优势与机会分析
            {{advantagesOpportunityAnalysis}}

            （二）风险与挑战提示
                主要风险：[风险类型]
                风险点：[具体风险点]
                影响：[可能后果]
                次要风险：[风险类型]
                风险点：[具体风险点]
                影响：[可能后果]
                程序性风险：[风险类型]
                风险点：[具体风险点]
                影响：[可能后果]

            四、行动建议与后续策略
            {{actionSuggestionsSubsequentStrategies}}

            # 工作原则
            - 仅基于提供的案件信息进行分析
            - 不臆测、不推演超出已知信息范围的内容
            - 不要输出任何其他提示信息。
            - 使用规范的法律术语
            - 避免绝对性表述（如"必然"、"一定"等）
            - 对风险点充分揭示，不作乐观估计
            """;

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

        // 创建工具集
        Toolkit toolkit = new Toolkit();

        // 获取系统提示词并注入当前时间
        String systemPrompt = injectCurrentTime(getSystemPrompt());

        ToolExecutionContext context = ToolExecutionContext.builder()
                .register(agentContext)
                .build();

        return ReActAgent.builder()
                .name(getAgentType().getCode())
                .sysPrompt(systemPrompt)
                .model(model)
                .maxIters(5)
                .toolkit(toolkit)
                .toolExecutionContext(context)
                .build();
    }

    @Override
    protected double getDefaultTemperature() {
        return 0.1; // 报告生成需要较低温度，保持专业性
    }

    @Override
    protected String determineStreamStatus(String chunkText, String fullText, int[] reportState) {
        return "message";
    }
}
