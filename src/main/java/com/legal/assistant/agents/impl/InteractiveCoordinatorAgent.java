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
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
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
     * 将事件转换为响应（覆盖基类方法）
     * 处理两种情况：
     * 1. 主Agent的普通文本输出 -> status: message
     * 2. 子Agent的报告内容（从ToolResultBlock提取）-> status: artifact
     */
    @Override
    protected StreamChatResponse convertEventToResponse(
            Event event,
            Long messageId,
            Long conversationId) {

        Msg msg = event.getMessage();
        if (msg == null || msg.getContent() == null) {
            return null;
        }

        // 额外保护：跳过 AGENT_RESULT 类型的事件（避免输出完整的最终结果）
        EventType eventType = event.getType();
        if (eventType == EventType.AGENT_RESULT) {
            log.debug("跳过AGENT_RESULT事件（主Agent最终结果，避免重复输出）");
            return null;
        }

        log.debug("处理事件: type={}, contentBlocks={}", eventType, msg.getContent().size());

        // 遍历消息内容
        for (ContentBlock block : msg.getContent()) {
            log.debug("内容块类型: {}", block.getClass().getSimpleName());
            
            // 1. 处理子Agent的报告内容（ToolResultBlock）
            if (block instanceof ToolResultBlock) {
                ToolResultBlock toolResult = (ToolResultBlock) block;
                String reportContent = extractReportFromToolResult(toolResult);
                if (reportContent != null && !reportContent.isEmpty()) {
                    log.debug("提取到报告内容片段: length={}", reportContent.length());
                    return new StreamChatResponse(
                            messageId,
                            conversationId,
                            reportContent,
                            "artifact",  // 子Agent报告内容为 artifact 状态
                            null,
                            false,
                            null
                    );
                }
            }
            
            // 2. 处理主Agent的普通文本输出（TextBlock）
            else if (block instanceof TextBlock) {
                TextBlock textBlock = (TextBlock) block;
                String text = textBlock.getText();
                if (text != null && !text.isEmpty()) {
                    log.debug("主Agent输出: {}", text.length() > 50 ? text.substring(0, 50) + "..." : text);
                    return new StreamChatResponse(
                            messageId,
                            conversationId,
                            text,
                            "message",  // 主Agent输出为 message 状态
                            null,
                            false,
                            null
                    );
                }
            }
        }

        return null;
    }

    /**
     * 从 ToolResultBlock 中提取报告内容
     */
    private String extractReportFromToolResult(ToolResultBlock toolResult) {
        if (toolResult == null) {
            log.debug("ToolResultBlock为null");
            return null;
        }
        
        if (toolResult.getOutput() == null) {
            log.debug("ToolResultBlock.getOutput()为null");
            return null;
        }

        // 提取 ToolResultBlock 中的文本内容
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : toolResult.getOutput()) {
            log.debug("ToolResultBlock内部块类型: {}", block.getClass().getSimpleName());
            if (block instanceof TextBlock) {
                String text = ((TextBlock) block).getText();
                log.debug("TextBlock内容(前100字符): {}", text != null && text.length() > 100 ? text.substring(0, 100) : text);
                if (text != null) {
                    sb.append(text);
                }
            }
        }

        String jsonContent = sb.toString();
        if (jsonContent.isEmpty()) {
            log.debug("ToolResultBlock中没有文本内容");
            return null;
        }

        // 如果内容不是以 { 开头，说明不是JSON格式的事件，可能是直接的报告文本
        // 这通常是子Agent的最终返回结果，应该跳过以避免重复输出
        String trimmed = jsonContent.trim();
        if (!trimmed.startsWith("{")) {
            log.debug("ToolResultBlock内容不是JSON格式，跳过（可能是子Agent最终结果）: length={}", trimmed.length());
            return null;
        }

        log.debug("准备解析JSON内容(前200字符): {}", jsonContent.length() > 200 ? jsonContent.substring(0, 200) : jsonContent);

        // 解析 JSON 提取实际的报告文本
        return parseSubAgentEventJson(jsonContent);
    }

    /**
     * 解析子Agent事件的 JSON，提取报告文本内容
     * JSON 格式: {"type":"REASONING","message":{"content":[{"type":"text","text":"报告内容"}]},...}
     */
    private String parseSubAgentEventJson(String jsonContent) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            log.debug("JSON内容为空");
            return null;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonContent);
            
            // 检查是否是子Agent的事件（包含 type 和 message 字段）
            if (!root.has("type") || !root.has("message")) {
                log.debug("JSON不包含type或message字段, 字段列表: {}", root.fieldNames());
                return null;
            }

            String eventType = root.get("type").asText();
            log.debug("事件类型: {}", eventType);
            
            // 只处理 REASONING 类型的事件（流式报告片段）
            // 跳过 AGENT_RESULT 类型（包含完整报告，会导致重复）
            if (!"REASONING".equals(eventType)) {
                if ("AGENT_RESULT".equals(eventType)) {
                    log.debug("跳过AGENT_RESULT事件（避免重复输出完整报告）");
                } else {
                    log.debug("跳过非REASONING类型事件: {}", eventType);
                }
                return null;
            }

            JsonNode messageNode = root.get("message");
            if (messageNode == null || !messageNode.has("content")) {
                log.debug("message节点不包含content字段");
                return null;
            }

            JsonNode contentArray = messageNode.get("content");
            if (contentArray == null || !contentArray.isArray()) {
                log.debug("content不是数组");
                return null;
            }

            StringBuilder textContent = new StringBuilder();
            for (JsonNode contentBlock : contentArray) {
                if (contentBlock.has("type") && "text".equals(contentBlock.get("type").asText())) {
                    if (contentBlock.has("text")) {
                        String text = contentBlock.get("text").asText();
                        log.debug("提取到文本: {}", text);
                        textContent.append(text);
                    }
                }
            }

            // 如果提取的文本太长（超过 200 字符），可能是完整报告而不是流式片段
            // 流式片段通常只有几个字到几十个字，完整报告有几千字
            // 跳过长文本以避免重复输出
            if (textContent.length() > 200) {
                log.debug("提取的文本过长（可能是完整报告），跳过以避免重复: length={}", textContent.length());
                return null;
            }

            String result = textContent.length() > 0 ? textContent.toString() : null;
            log.debug("最终提取结果: {}", result);
            return result;
        } catch (Exception e) {
            log.warn("解析子Agent事件JSON失败: {}, 内容: {}", e.getMessage(), 
                    jsonContent.length() > 200 ? jsonContent.substring(0, 200) : jsonContent);
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
            
            2. **立即调用 generate_risk_assessment_report 工具**：
               - 这是必须的步骤，不能跳过
               - 调用示例：
                 Action: generate_risk_assessment_report
                 Action Input: {"caseInfo": "委托方：张三（原告）；对方：李四面馆（被告）；核心诉求：索赔1000元；基本事实：2024年X月X日在李四面馆就餐发现苍蝇，已取证；现有证据：照片、付款记录"}
               - 将收集到的所有案件信息作为参数传入
            
            3. **等待工具执行完成**（工具会输出完整的风险评估报告）
            
            4. **输出完成提示**：
               "风险评估报告已生成完成，如需下载PDF版本，请告诉我。"
            
            ## 第四步：生成下载报告地址（用户请求时执行）
            当用户表示需要下载报告时：
            1. 首先调用 get_last_report_id 工具获取最近生成的报告ID
            2. 使用获取到的报告ID调用 generate_download_link 工具生成下载链接
            3. 将生成的下载地址返回给用户
            
            # 可用工具
            
            1. **generate_risk_assessment_report**（最重要的工具）
               - 作用：生成专业的风险评估报告
               - 使用时机：信息收集完成后，必须立即调用
               - 参数格式：案件描述文本（包含委托方、对方、诉求、事实、证据）
               - 重要：这是唯一能生成报告的方式，不能自己生成
            
            2. **getFileContent**
               - 作用：查看用户提供的文件内容
            
            3. **get_last_report_id**
               - 作用：获取当前会话中最近生成的报告ID
               - 使用时机：用户请求下载报告时，先调用此工具获取报告ID
            
            4. **generate_download_link**
               - 作用：生成报告下载链接
               - 参数：报告ID（通过 get_last_report_id 获取）
            
            # 关键原则
            - ✅ 信息完整后必须调用 generate_risk_assessment_report 工具
            - ✅ 使用工具后等待工具输出完成
            - ✅ 工具调用是必须的，不是可选项
            """;
}
