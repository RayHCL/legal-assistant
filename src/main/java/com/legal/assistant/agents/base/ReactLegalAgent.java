package com.legal.assistant.agents.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.agents.tools.FileToolService;
import com.legal.assistant.dto.response.StreamChatResponse;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;

import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * ReAct法律Agent基类
 */
@Slf4j
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

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 将当前时间注入到系统提示词中
     * 替换提示词中的占位符 {current_time}
     */
    protected String injectCurrentTime(String systemPrompt) {
        String currentTime = getCurrentDate();
        return systemPrompt.replace("{current_time}", currentTime);
    }

    protected String getCurrentDate(){
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        LocalDateTime now = LocalDateTime.now(zoneId);
        String formattedTime = now.format(DATE_FORMATTER);
        return "当前日期: " + formattedTime + " (时区: " + zoneId + ")";
    }

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

        // 获取系统提示词并注入当前时间
        String systemPrompt = injectCurrentTime(getSystemPrompt());

        ToolExecutionContext context = ToolExecutionContext.builder()
                .register(agentContext)
                .build();
        return ReActAgent.builder()
                .name(getAgentType().getCode())
                .sysPrompt(systemPrompt)
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

    /**
     * 流式对话方法 - 返回组装好的流式响应
     *
     * @param agent          已配置的ReActAgent实例
     * @param userInput      用户输入
     * @param messageId      消息ID
     * @param conversationId 会话ID
     * @return Flux<StreamChatResponse> 流式响应
     */
    public Flux<StreamChatResponse> streamChat(
            ReActAgent agent,
            String userInput,
            Long messageId,
            Long conversationId) {

        // 配置流式输出选项 - 接收所有类型的事件
        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.ALL)  // 接收所有事件，包括REASONING, TOOL_RESULT, 普通文本等
                .incremental(true)
                .includeReasoningResult(false)  // 不包含推理过程结果，只包含最终输出
                .includeActingChunk(true)
                .build();

        // 创建输入消息
        Msg inputMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(userInput)
                .build();

        // 用于累积完整内容（用于状态判断）
        final StringBuilder accumulatedText = new StringBuilder();

        // 状态：0=正常输出, 1=报告开始检测, 2=报告生成中
        final int[] reportState = {0};

        // 执行流式推理并组装响应
        return agent.stream(inputMsg, streamOptions)
                .filter(event -> {
                    // 保留所有有内容的事件
                    Msg message = event.getMessage();
                    if (message == null) {
                        return false;
                    }

                    String content = message.getTextContent();
                    // 只要有内容就保留
                    return content != null && !content.isEmpty();
                })
                .map(event -> {
                    EventType eventType = event.getType();
                    Msg message = event.getMessage();
                    String content = message.getTextContent();

                    // 根据事件类型和内容判断status
                    String status;

                    if (eventType == EventType.TOOL_RESULT) {
                        // 🔧 工具结果（明确的事件类型）
                        log.info("📢 [TOOL_RESULT] 工具结果: {}", content);
                        return createToolResultResponse(message, messageId, conversationId);
                    } else if (eventType == EventType.REASONING) {
                        // REASONING 事件需要根据内容判断具体类型

                        // 检查是否是工具返回的简单内容（日期、时间、数字等）
                        boolean isToolResult = false;

                        // 日期格式：2026-01-27
                        if (content.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                            isToolResult = true;
                        }
                        // 时间格式：12:34:56
                        else if (content.matches("^\\d{2}:\\d{2}:\\d{2}$")) {
                            isToolResult = true;
                        }
                        // 纯数字
                        else if (content.matches("^\\d+$")) {
                            isToolResult = true;
                        }
                        // URL格式（MinIO路径等）
                        else if (content.matches("^(http|https|minio)://.*")) {
                            isToolResult = true;
                        }
                        // 文件路径
                        else if (content.matches("^[\\w-]+\\.md$")) {
                            isToolResult = true;
                        }
                        // 报告ID格式
                        else if (content.matches("^RPT\\d+$")) {
                            isToolResult = true;
                        }

                        if (isToolResult) {
                            log.info("📢 [TOOL_RESULT] 工具结果(识别): {}", content);
                            return createToolResultResponse(message, messageId, conversationId);
                        }

                        // 累积内容用于更准确的判断
                        accumulatedText.append(content);
                        String fullText = accumulatedText.toString();

                        // 检查是否是报告开始
                        if (fullText.contains("关于\"") && fullText.contains("案的风险评估报告")) {
                            status = "artifact";
                            log.info("📢 [ARTIFACT] 报告内容: {}", content.substring(0, Math.min(50, content.length())));
                        } else {
                            // 默认为普通消息
                            status = "message";
                            log.debug("📢 [MESSAGE] 普通消息: {}", content.substring(0, Math.min(50, content.length())));
                        }
                    } else {
                        // 其他事件类型，使用子类定制的状态判断
                        accumulatedText.append(content);
                        String fullText = accumulatedText.toString();
                        status = determineStreamStatus(content, fullText, reportState);
                        log.debug("📢 [{}] 状态: {}", status, content.substring(0, Math.min(50, content.length())));
                    }

                    return new StreamChatResponse(
                            messageId,
                            conversationId,
                            content,
                            status,
                            null, // 中间过程不返回title
                            false,
                            null // 普通文本没有工具调用信息
                    );
                })
                .filter(response -> response != null);
    }

    /**
     * 创建工具结果响应
     */
    private StreamChatResponse createToolResultResponse(Msg message, Long messageId, Long conversationId) {
        try {
            String toolResult = message.getTextContent() != null ? message.getTextContent() : "";

            log.info("📢 [TOOL_RESULT] 创建工具结果响应: {}", toolResult.substring(0, Math.min(50, toolResult.length())));

            StreamChatResponse.ToolCallInfo toolCallInfo = new StreamChatResponse.ToolCallInfo(
                    "tool", // 工具名
                    null,
                    toolResult,
                    false,
                    true
            );

            return new StreamChatResponse(
                    messageId,
                    conversationId,
                    toolResult,
                    "tool_result",
                    null,
                    false,
                    toolCallInfo
            );
        } catch (Exception e) {
            log.error("创建工具结果响应失败", e);
            return null;
        }
    }

    /**
     * 创建完成响应
     */
    public StreamChatResponse createCompletionResponse(
            Long messageId,
            Long conversationId,
            String generatedTitle,
            boolean isError,
            String errorMessage) {

        return new StreamChatResponse(
                messageId,
                conversationId,
                isError ? errorMessage : "",
                isError ? "error" : "completed",
                generatedTitle,
                true,
                null
        );
    }

    /**
     * 判断流式输出的状态
     * 子类可以重写此方法来定制状态判断逻辑
     *
     * @param chunkText   当前文本块
     * @param fullText    累积的完整文本
     * @param reportState 报告状态数组 [当前状态]
     * @return 状态字符串
     */
    protected String determineStreamStatus(String chunkText, String fullText, int[] reportState) {
        // 默认实现：所有输出都是普通消息
        // 子类（如ReportGenerationAgent）可以重写此方法来实现特殊的状态判断
        return "message";
    }
}
