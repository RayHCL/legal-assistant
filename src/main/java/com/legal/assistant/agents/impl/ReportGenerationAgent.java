package com.legal.assistant.agents.impl;

import com.legal.assistant.agents.base.ReactLegalAgent;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.dto.response.StreamChatResponse;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 报告生成Agent - 专注于生成专业的风险评估报告
 */
@Slf4j
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
            -summary: 总结  
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
            {{summary}}
            
            # 示例（与实际案例没有关系，只参照格式）
            
            # 关于“XX公司与YY公司买卖合同纠纷”案的风险评估报告
            
            致：XX公司
            
            日期：2025年10月27日
            
            案由：买卖合同纠纷
            
            ## 一、报告基础与声明
            
            本报告基于2025年10月27日提供的案件所述信息作出。本报告旨在对案件进行初步的、方向性的风险评估，并非对诉讼结果的承诺。随着案件证据的补充和程序的推进，评估结论可能发生变化。本报告为内部法律分析文件，请注意保密。
            
            ## 二、案件核心事实梳理
            
            我方当事人：XX公司
            
            对方当事人：YY公司
            
            核心诉求：追索YY公司拖欠的货款本金人民币100万元及相应逾期利息。
            
            ### 基本事实：
            
            - 2025年1月15日：双方签订《产品采购合同》，约定货款总额150万元。
            - 2025年2月10日：我方依约交付全部货物。
            - 2023年2月28日：对方支付首笔货款50万元。
            - 约定付款日2023年3月31日：剩余100万元货款到期。
            - 至今：经多次催收，对方以“资金周转困难”、“产品质量有瑕疵”为由拒绝支付。
            
            ### 现有核心证据：
            
            《产品采购合同》原件、发货单签收记录、银行转账记录50万元、催收往来电子邮件/微信聊天记录。
            
            ## 三、初步风险评估
            
            综合风险：20（中低风险）
            
            ### （一）优势与机会分析
            
            1. 债权债务关系清晰：根据现有信息，双方存在书面合同，我方履约（交货）事实明确，对方部分履约（付款）事实清晰，足以构建一个完整的债权债务链条。这是本案最大的优势。
            2. 证据基础较为扎实：拥有合同、交付凭证、付款凭证等核心书证，证据链相对完整，证明力较强。
            3. 对方抗辩理由初步评估：对方提出的“资金周转困难”属于事实履行障碍，而非法律上的有效抗辩，不构成拒付货款的合法理由。其提及的“产品质量瑕疵”若无法提供有效证据（如第三方检测报告、双方确认的质量问题记录等），则难以得到法院支持。
            
            ### （二）风险与挑战提示
            
            #### 主要风险：证据细节缺失
            
            - 风险点：信息收集表中未明确合同关于产品质量验收期限和异议方式的条款。这是对方可能发起反击的核心点。如果合同约定买方在收货后特定时间内（如7-15天）未提出书面异议视为验收合格，则我方优势极大；反之，若合同对此约定不明，对方可能利用程序拖延诉讼。
            - 影响：该风险直接影响案件的对抗强度和审理周期。
            
            #### 次要风险：对方偿付能力
            
            - 风险点：对方“资金周转困难”的表态可能反映其真实的偿付能力问题。
            - 影响：即便获得胜诉判决，若对方无足够财产可供执行，仍存在“执行难”的风险，导致经济损失无法实际挽回。
            
            #### 程序性风险：诉讼成本与时间
            
            - 风险点：诉讼需要投入时间、律师费及诉讼费。虽然本案事实清楚，但若对方滥用程序（如提起管辖权异议、上诉等），会拉长解决周期。
            
            ## 四、行动建议与后续策略
            
            ### 首要行动——补充关键证据：
            
            1. 立即核查《产品采购合同》，找到并固定关于“验收条款”和“逾期付款违约责任”的约定。这是当前最紧急且重要的一步。
            2. 系统化整理所有催收记录，形成连续的催讨时间线，以中断诉讼时效，并证明我方一直在主张权利。
            
            ### 策略建议——诉前准备与谈判并行：
            
            1. 财产摸底：建议通过合法途径对YY公司的资产状况（房产、车辆、银行账户、股权等）进行初步调查，评估其偿付能力。
            2. 发送正式律师函：在证据准备充分后，可考虑以律师事务所名义向YY公司发送一份措辞严谨的律师函。此举既能作为最后的催告，也可能促成庭前和解，以更低成本解决纠纷。
            3. 制定诉讼方案：同步准备起诉状及证据材料。一旦谈判破裂，可立即启动诉讼程序，并视情况申请财产保全，查封对方资产，以保障最终判决的执行。
            
            ### 预期与展望：
            
            1. 在验收条款对我方有利的前提下，本案通过诉讼获得胜诉判决的可能性很高。
            2. 案件的关键在于能否通过财产保全等措施有效控制对方资产，从而将“胜诉权”转化为“实在的经济利益”。
            
            ### 结论：
            
            综上所述，贵司在本案中法律地位占优，但需立即补强证据细节，并启动对对方偿付能力的调查。建议采取“边谈边打”的策略，力争以最小成本实现债权。 
            
            
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

        // 构建Agent，注册ReportSaveHook钩子
        return  ReActAgent.builder()
                .name(getAgentType().name())
                .sysPrompt(systemPrompt)
                .model(model)
                .maxIters(3)
                .toolkit(toolkit)
                .toolExecutionContext(context)
                .build();



    }

    @Override
    protected double getDefaultTemperature() {
        return 0.1; // 报告生成需要较低温度，保持专业性
    }

    /**
     * 流式对话方法 - 覆盖基类方法
     * 报告生成Agent的所有输出都标记为artifact状态
     */
    @Override
    public Flux<StreamChatResponse> streamChat(
            ReActAgent agent,
            String userInput,
            Long messageId,
            Long conversationId) {

        // 创建用户消息
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(userInput)
                .build();

        // 配置流式选项
        StreamOptions streamOptions = createStreamOptions();

        log.info("ReportGenerationAgent 开始流式对话: messageId={}, conversationId={}",
                messageId, conversationId);

        // 执行流式推理并转换为 StreamChatResponse
        // 所有输出都标记为 artifact 状态
        return agent.stream(userMsg, streamOptions)
                .filter(event -> event != null && event.getMessage() != null)
                .flatMap(event -> Mono.justOrEmpty(convertEventToArtifactResponse(event, messageId, conversationId)))
                .filter(response -> response.getContent() != null && !response.getContent().isEmpty())
                .doOnError(error -> log.error("ReportGenerationAgent 流式对话错误: messageId={}, error={}",
                        messageId, error.getMessage()))
                .doOnComplete(() -> log.info("ReportGenerationAgent 流式对话完成: messageId={}", messageId));
    }

    /**
     * 将事件转换为artifact状态的StreamChatResponse
     * 报告生成Agent的所有文本输出都使用artifact状态
     */
    private StreamChatResponse convertEventToArtifactResponse(
            Event event,
            Long messageId,
            Long conversationId) {

        Msg msg = event.getMessage();
        if (msg == null) {
            return null;
        }

        // 获取文本内容
        String content = getTextContent(msg);
        if (content == null || content.isEmpty()) {
            return null;
        }

        // 报告生成Agent的所有输出都是artifact
        return new StreamChatResponse(
                messageId,
                conversationId,
                content,
                "artifact",  // 固定为artifact状态
                null,
                false,
                null
        );
    }

    /**
     * 覆盖状态判断方法 - 所有输出都是artifact
     */
    @Override
    protected String determineStreamStatus(Event event, String content) {
        return "artifact";
    }

    @Override
    protected String determineStreamStatus(String chunkText, String fullText, int[] reportState) {
        return "artifact";
    }
}
