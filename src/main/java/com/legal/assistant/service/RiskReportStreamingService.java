package com.legal.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.agents.factory.LegalAgentFactory;
import com.legal.assistant.dto.response.StreamChatResponse;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 风险报告流式生成服务
 */
@Slf4j
@Service
public class RiskReportStreamingService {

    @Autowired
    private LegalAgentFactory agentFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 流式生成风险评估报告
     * @param caseDescription 案件描述
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param messageId 消息ID
     * @return 流式响应
     */
    public Flux<String> generateReportStream(
            String caseDescription,
            Long userId,
            Long conversationId,
            Long messageId) {

        // 构建报告生成提示词
        String reportPrompt = buildReportGenerationPrompt(caseDescription);

        // 创建AgentContext
        AgentContext reportAgentContext = new AgentContext(userId, conversationId);

        // 创建ReportGenerationAgent
        ReActAgent reportAgent = agentFactory.createAgent(
                AgentType.REPORT_GENERATION,
                ModelType.DASHSCOPE_QWEN_MAX,
                0.3,
                reportAgentContext
        );

        // 配置流式输出选项
        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                .incremental(true)
                .includeReasoningResult(false)
                .build();

        // 创建输入消息
        Msg inputMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(reportPrompt)
                .build();

        log.info("开始流式生成风险评估报告: userId={}, conversationId={}, messageId={}",
                userId, conversationId, messageId);

        // 执行流式推理
        return Flux.create(emitter -> {
            StringBuilder contentBuilder = new StringBuilder();

            reportAgent.stream(inputMsg, streamOptions)
                    .subscribe(
                            event -> {
                                // 处理流式事件
                                Msg message = event.getMessage();
                                if (message != null) {
                                    String content = message.getTextContent();
                                    if (content != null && !content.isEmpty()) {
                                        contentBuilder.append(content);

                                        // 发送流式响应（status=artifact表示这是报告内容）
                                        try {
                                            StreamChatResponse response = new StreamChatResponse(
                                                    messageId,
                                                    conversationId,
                                                    content,
                                                    "artifact",  // 使用artifact状态标识这是报告
                                                    null,
                                                    false
                                            );
                                            emitter.next(objectMapper.writeValueAsString(response));
                                        } catch (Exception e) {
                                            log.error("序列化响应失败", e);
                                        }
                                    }
                                }
                            },
                            error -> {
                                log.error("流式生成报告失败: userId={}, conversationId={}",
                                        userId, conversationId, error);
                                try {
                                    StreamChatResponse response = new StreamChatResponse(
                                            messageId,
                                            conversationId,
                                            "生成报告失败: " + error.getMessage(),
                                            "error",
                                            null,
                                            true
                                    );
                                    emitter.next(objectMapper.writeValueAsString(response));
                                    emitter.complete();
                                } catch (Exception e) {
                                    emitter.error(e);
                                }
                            },
                            () -> {
                                log.info("流式生成报告完成: userId={}, conversationId={}, 报告长度={}",
                                        userId, conversationId, contentBuilder.length());

                                try {
                                    // 发送完成事件
                                    StreamChatResponse response = new StreamChatResponse(
                                            messageId,
                                            conversationId,
                                            "",
                                            "completed",
                                            null,
                                            true
                                    );
                                    emitter.next(objectMapper.writeValueAsString(response));
                                    emitter.complete();
                                } catch (Exception e) {
                                    log.error("发送完成事件失败", e);
                                    emitter.error(e);
                                }
                            }
                    );
        });
    }

    /**
     * 构建报告生成的提示词
     */
    private String buildReportGenerationPrompt(String caseDescription) {
        return String.format("""
                请根据以下案件描述，生成一份专业的风险评估报告。

                ## 案件信息

                %s

                ## 你的任务

                请从上述案件描述中**提取并分析**以下信息：

                ### 1. 信息提取
                - 我方当事人及身份
                - 对方当事人及身份
                - 案由
                - 核心诉求
                - 基本事实
                - 现有证据

                ### 2. 风险分析
                - **风险等级判断**（overallRiskLevel）：
                  * 较低风险：证据充分，法律关系清晰，胜诉把握大
                  * 中等风险：证据或事实存在瑕疵，需补充完善
                  * 较高风险：证据不足，法律关系复杂，存在重大障碍

                - **风险评分**（overallRiskScore）：
                  * 较低：10-30分
                  * 中等：40-70分
                  * 较高：80-100分

                - **优势分析**（advantagesOpportunityAnalysis）：
                  列出有利因素，分点简述

                - **风险提示**（riskChallengeAlert）：
                  严格按格式：主要风险：[描述] 风险点：[具体] 影响：[后果]

                - **风险点简述**（riskPoint）：
                  提取核心风险点，每条20字内，空格分隔

                - **行动建议**（actionSuggestionsSubsequentStrategies）：
                  首要行动+策略建议+预期展望+总结

                ### 3. 保存报告
                完成分析后，调用 `save_risk_report_to_db` 工具保存报告。

                ## 报告结构要求

                1. 案件事实梳理
                2. 综合风险等级和评分
                3. 优势与机会分析
                4. 风险与挑战提示
                5. 行动建议与后续策略

                报告应当客观、专业、有理有据。基于案件描述进行分析，不要杜撰信息。
                """,
                caseDescription
        );
    }
}
