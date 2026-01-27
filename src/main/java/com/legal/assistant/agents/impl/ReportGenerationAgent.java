package com.legal.assistant.agents.impl;

import com.legal.assistant.agents.base.ReactLegalAgent;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.agents.tools.ReportSaveToolService;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.agentscope.core.ReActAgent;
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

    // ==================== 依赖注入 ====================
    @Autowired(required = false)
    private ReportSaveToolService reportSaveTool;


    // ==================== 系统提示词 ====================

    /**
     * 报告生成Agent系统提示词
     */
    private static final String REPORT_GENERATION_SYSTEM_PROMPT = """
            # 职责定位
            您是法律风险评估专家系统，负责对案件信息进行专业分析，生成并保存风险评估报告。

            # 当前时间
            {current_time}

            # 工作流程

            ## 第一步：输出完整报告
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

            ## 第二步：保存报告
            报告输出完成后，调用 save_report_to_minio 工具，将刚才输出的完整报告内容作为参数传入

            ## 第三步：输出完成标记
            工具返回后，仅输出以下标记（不要输出其他内容）：
            "风险评估报告已生成完成"

            注意：不要输出任何其他提示信息，后续提示由协调器Agent处理。

            # 分析标准

            ## 风险等级与评分
            - **较低风险（10-30分）**：证据充分确凿，法律关系清晰，适用法律明确，胜诉可能性高
            - **中等风险（40-70分）**：证据基本充分但存在瑕疵，或法律适用存在争议，需补充证据
            - **较高风险（80-100分）**：证据不足或法律关系复杂，存在重大不确定性，败诉可能性高
            ## 案由根据案件在以下案由中选择
            合同纠纷(买卖纠纷、租赁纠纷、借贷纠纷、建设工程纠纷)、公司纠纷、知识产权纠纷、金融纠纷、国际贸易纠纷、电子商务纠纷、房地产纠纷

            # 工作原则
            - 仅基于提供的案件信息进行分析
            - 不臆测、不推演超出已知信息范围的内容
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

        // 创建工具集并注册工具
        Toolkit toolkit = new Toolkit();

        // 注册报告保存工具
        if (reportSaveTool != null) {
            toolkit.registerTool(reportSaveTool);
        }

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
        return 0.3; // 报告生成需要较低温度，保持专业性
    }

    @Override
    protected String determineStreamStatus(String chunkText, String fullText, int[] reportState) {
        // 状态：0=准备阶段, 1=报告生成阶段, 2=完成标记阶段

        // 检测报告开始（标题格式：关于"xxx与xxx"案的风险评估报告）（状态1）
        if (fullText.contains("关于\"") && fullText.contains("案的风险评估报告")) {
            reportState[0] = 1;
        }

        // 检测完成标记（"风险评估报告已生成完成"）（状态2）
        if (fullText.contains("风险评估报告已生成完成")) {
            reportState[0] = 2;
        }

        // 根据当前状态确定status
        if (reportState[0] == 1) {
            // 报告生成阶段，所有输出都是artifact
            return "artifact";
        } else {
            // 其他阶段都是普通消息
            return "message";
        }
    }
}
