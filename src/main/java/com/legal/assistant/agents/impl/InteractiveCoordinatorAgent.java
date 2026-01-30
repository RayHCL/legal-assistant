package com.legal.assistant.agents.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legal.assistant.agents.base.ReactLegalAgent;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.agents.tools.ReportSaveToolService;
import com.legal.assistant.dto.response.StreamChatResponse;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.*;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 交互协调器Agent - 负责信息收集和流程控制
 */
@Slf4j
@Component
public class InteractiveCoordinatorAgent extends ReactLegalAgent {

    // JSON解析器
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
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
        boolean enableThinking = Boolean.TRUE.equals(agentContext != null ? agentContext.getDeepThinking() : null);
        GenerateOptions options = enableThinking
                ? GenerateOptions.builder().temperature(temperature).thinkingBudget(5000).build()
                : GenerateOptions.builder().temperature(temperature).build();
        DashScopeChatModel model = enableThinking
                ? DashScopeChatModel.builder().apiKey(apiKey).defaultOptions(options).modelName(modelName).enableThinking(true).build()
                : DashScopeChatModel.builder().apiKey(apiKey).defaultOptions(options).modelName(modelName).build();

        // 创建记忆
        Memory memory = createMemory(model);

        // 创建工具集并注册工具
        Toolkit toolkit = new Toolkit();

        // 注册基础工具
        if (fileToolService != null) {
            toolkit.registerTool(fileToolService);
        }
        if (reportSaveToolService != null) {
            toolkit.registerTool(reportSaveToolService);
        }

        // 注册报告生成Agent作为子Agent工具
        if (reportGenerationAgent != null) {
            toolkit.registration()
                    .subAgent(() -> reportGenerationAgent.configure(modelType, temperature, agentContext),
                            SubAgentConfig.builder()
                                    .toolName("generate_risk_assessment_report")
                                    .description("【重要工具】生成专业的风险评估报告。当收集到完整的案件信息后，必须调用此工具来生成风险评估报告。工具接收案件描述文本，分析后生成包含风险等级、评分、分析和建议的完整报告。")
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
                .name(getAgentType().name())
                .sysPrompt(systemPrompt)
                .model(model)
                .memory(memory)
                .maxIters(5)  // 增加迭代次数，确保有足够的机会调用工具
                .toolkit(toolkit)
                .toolExecutionContext(context)
                .build();
    }

    @Override
    protected double getDefaultTemperature() {
        return 0.7;
    }

    /**
     * 覆盖流式选项配置 - 只订阅 REASONING 和 TOOL_RESULT 事件
     * 排除 AGENT_RESULT 事件，避免在流式输出完成后重复输出完整内容
     */
    @Override
    protected StreamOptions createStreamOptions() {
        return StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)  // 不包含 AGENT_RESULT
                .incremental(true)
                .includeReasoningResult(false)  // 不包含最终推理结果（避免重复）
                .includeActingChunk(true)
                .build();
    }

    /**
     * 流式对话方法 - 覆盖基类方法以处理子Agent事件
     * 输出主智能体的消息（message）和子智能体的报告内容（artifact）
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

        log.info("InteractiveCoordinatorAgent 开始流式对话: messageId={}, conversationId={}",
                messageId, conversationId);

        // 执行流式推理并转换为 StreamChatResponse
        return agent.stream(userMsg, streamOptions)
                .filter(event -> event != null && event.getMessage() != null)
                .flatMap(event -> {
                    // 转换事件为响应（包括主Agent消息和子Agent报告）
                    StreamChatResponse response = convertEventToResponse(event, messageId, conversationId);
                    return Mono.justOrEmpty(response);
                })
                .filter(response -> response.getContent() != null && !response.getContent().isEmpty())
                .doOnError(error -> log.error("InteractiveCoordinatorAgent 流式对话错误: messageId={}, error={}",
                        messageId, error.getMessage()))
                .doOnComplete(() -> log.info("InteractiveCoordinatorAgent 流式对话完成: messageId={}", messageId));
    }

    /**
     * 将事件转换为响应（优化版：一次遍历处理所有逻辑）
     * 处理两种情况：
     * 1. 主Agent的思考输出 -> status: thinking
     * 2. 主Agent的普通文本输出 -> status: message
     * 3. 子Agent的报告内容（从ToolResultBlock提取）-> status: artifact
     */
    @Override
    protected StreamChatResponse convertEventToResponse(
            Event event,
            Long messageId,
            Long conversationId) {

        Msg msg = event.getMessage();
        List<ContentBlock> contents;
        if (msg == null || (contents = msg.getContent()) == null || contents.isEmpty()) {
            return null;
        }

        // 跳过 AGENT_RESULT 类型的事件（避免输出完整的最终结果）
        if (event.getType() == EventType.AGENT_RESULT) {
            return null;
        }

        // 一次遍历处理所有内容块
        for (ContentBlock block : contents) {
            // 处理子Agent的报告内容（ToolResultBlock）- 优先级最高
            if (block instanceof ToolResultBlock) {
                String reportContent = extractReportFromToolResult((ToolResultBlock) block);
                if (reportContent != null && !reportContent.isEmpty()) {
                    return StreamChatResponse.message(messageId, conversationId, reportContent, "artifact");
                }
            }
            // 处理主Agent的普通文本输出（TextBlock）
            else if (block instanceof TextBlock) {
                String text = ((TextBlock) block).getText();
                if (text != null && !text.isEmpty()) {
                    return StreamChatResponse.message(messageId, conversationId, text, "message");
                }
            }
            else if (block instanceof ThinkingBlock){
                String thinking = ((ThinkingBlock) block).getThinking();
                if (thinking != null && !thinking.isEmpty()){
                    return StreamChatResponse.message(messageId, conversationId, thinking, "thinking");
                }

            }
        }

        return null;
    }

    /**
     * 从 ToolResultBlock 中提取报告内容（优化版）
     */
    private String extractReportFromToolResult(ToolResultBlock toolResult) {
        List<ContentBlock> output = toolResult.getOutput();
        if (output == null || output.isEmpty()) {
            return null;
        }

        // 提取文本内容
        StringBuilder sb = null;
        for (ContentBlock block : output) {
            if (block instanceof TextBlock) {
                String text = ((TextBlock) block).getText();
                if (text != null && !text.isEmpty()) {
                    if (sb == null) {
                        sb = new StringBuilder(text);
                    } else {
                        sb.append(text);
                    }
                }
            }
        }

        if (sb == null || sb.length() == 0) {
            return null;
        }

        String content = sb.toString();
        // 快速检查：非JSON格式直接跳过
        char firstChar = content.charAt(0);
        if (firstChar != '{' && (firstChar != ' ' || !content.trim().startsWith("{"))) {
            return null;
        }

        // 解析JSON提取报告文本
        return parseSubAgentEventJson(content.trim());
    }

    /**
     * 解析子Agent事件的JSON（优化版：减少不必要的日志和检查）
     * JSON 格式: {"type":"REASONING","message":{"content":[{"type":"text","text":"报告内容"}]},...}
     */
    private String parseSubAgentEventJson(String json) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);

            // 快速检查必需字段
            JsonNode typeNode = root.get("type");
            JsonNode messageNode = root.get("message");
            if (typeNode == null || messageNode == null) {
                return null;
            }

            // 只处理 REASONING 类型（流式报告片段）
            if (!"REASONING".equals(typeNode.asText())) {
                return null;
            }

            JsonNode contentArray = messageNode.get("content");
            if (contentArray == null || !contentArray.isArray()) {
                return null;
            }

            // 提取文本内容
            StringBuilder textContent = null;
            for (JsonNode contentBlock : contentArray) {
                JsonNode blockType = contentBlock.get("type");
                JsonNode textNode = contentBlock.get("text");
                if (blockType != null && "text".equals(blockType.asText()) && textNode != null) {
                    String text = textNode.asText();
                    if (text != null && !text.isEmpty()) {
                        if (textContent == null) {
                            textContent = new StringBuilder(text);
                        } else {
                            textContent.append(text);
                        }
                    }
                }
            }

            // 跳过过长的文本（可能是完整报告而非流式片段）
            if (textContent == null || textContent.length() == 0 || textContent.length() > 200) {
                return null;
            }

            return textContent.toString();
        } catch (Exception e) {
            log.warn("解析子Agent事件JSON失败: {}", e.getMessage());
            return null;
        }
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
            
            2. **生成风险评估报告**：
               - 这是必须的步骤，不能跳过
               - 将收集到的所有案件信息作为参数传入
            
            3. **等待生成报告完成**（输出完整的风险评估报告）
            
            4. **输出完成提示**：
               "风险评估报告已生成完成，如需下载PDF版本，请告诉我。"
            
            ## 第四步：生成下载报告地址（用户请求时执行）
            当用户表示需要下载报告时： 生成PDF的下载链接
           
            """;
}
