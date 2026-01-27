package com.legal.assistant.agents.base;

import com.fasterxml.jackson.databind.JsonNode;
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
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ReAct法律Agent基类
 */
@Slf4j
public abstract class ReactLegalAgent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            Long conversationId,
            boolean supportsThinking) {

        // 配置流式输出选项 - 接收所有类型的事件
        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.ALL)  // 接收所有事件，包括REASONING, TOOL_RESULT, 普通文本等
                .incremental(true)
                .includeReasoningResult(false)
                .includeActingChunk(true)
                .build();

        // 创建输入消息
        Msg inputMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(userInput)
                .build();

        // 是否已有增量输出（防止final_result重复完整内容）
        final boolean[] hasIncrementalOutput = {false};
        // 子Agent完成提示缓冲（避免转发输出完成提示）
        final String reportCompletedMarker = "风险评估报告已生成完成";
        final StringBuilder forwardedMarkerBuffer = new StringBuilder();

        // 执行流式推理并组装响应
        return agent.stream(inputMsg, streamOptions)
                .map(event -> {
                    Msg message = event.getMessage();
                    String rawContent = extractTextContent(message);
                    ForwardedEvent forwardedEvent = unwrapForwardedEvent(rawContent);
                    String content = forwardedEvent.isForwardedEvent ? forwardedEvent.text : rawContent;
                    return new StreamEventPayload(event, content, forwardedEvent.isForwardedEvent);
                })
                .filter(payload -> payload.content != null && !payload.content.isEmpty())
                .handle((payload, sink) -> {
                    EventType eventType = payload.event.getType();
                    String content = payload.content;
                    boolean isForwardedEvent = payload.isForwardedEvent;

                    // 根据事件类型判断status
                    String status;
                    boolean isFinalResult = isFinalResultEvent(eventType);

                    if (isForwardedEvent) {
                        // 过滤子Agent的完成提示（可能被拆分为多段）
                        if (content != null && !content.isEmpty()) {
                            forwardedMarkerBuffer.append(content);
                            String buffered = forwardedMarkerBuffer.toString();
                            if (reportCompletedMarker.startsWith(buffered)) {
                                if (reportCompletedMarker.equals(buffered)) {
                                    forwardedMarkerBuffer.setLength(0);
                                }
                                return;
                            }
                            // 非完成提示，合并缓冲内容作为真实输出
                            content = buffered;
                            forwardedMarkerBuffer.setLength(0);
                        }
                    } else if (forwardedMarkerBuffer.length() > 0) {
                        // 非转发事件到来，清空可能残留的完成提示缓冲
                        forwardedMarkerBuffer.setLength(0);
                    }

                    boolean isReportCompletedMarker = reportCompletedMarker.equals(content.trim());

                    if (eventType == EventType.REASONING || (isForwardedEvent && !isFinalResult)) {
                        hasIncrementalOutput[0] = true;
                    }

                    if (isFinalResult && hasIncrementalOutput[0]) {
                        // 已有增量输出时，跳过final_result的重复完整内容
                        return;
                    } else if (isReportCompletedMarker) {
                        status = "message";
                    } else if (isForwardedEvent) {
                        // 子Agent输出（报告生成），统一标记为artifact
                        status = "artifact";
                    } else if (eventType == EventType.REASONING) {
                        // REASONING 仅在模型支持深度思考时标记为thinking
                        status = supportsThinking ? "thinking" : "message";
                    } else {
                        // 其他事件默认普通消息
                        status = "message";
                    }

                    sink.next(new StreamChatResponse(
                            messageId,
                            conversationId,
                            content,
                            status,
                            null, // 中间过程不返回title
                            false,
                            null // 普通文本没有工具调用信息
                    ));
                });
    }

    private String extractTextContent(Msg message) {
        if (message == null) {
            return null;
        }

        String textContent = message.getTextContent();
        if (textContent != null && !textContent.isEmpty()) {
            return textContent;
        }

        List<ToolResultBlock> toolResults = message.getContentBlocks(ToolResultBlock.class);
        if (toolResults == null || toolResults.isEmpty()) {
            return textContent;
        }

        StringBuilder builder = new StringBuilder();
        for (ToolResultBlock toolResult : toolResults) {
            List<ContentBlock> output = toolResult.getOutput();
            if (output == null || output.isEmpty()) {
                continue;
            }
            for (ContentBlock block : output) {
                if (block instanceof TextBlock) {
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append(((TextBlock) block).getText());
                }
            }
        }

        return builder.length() > 0 ? builder.toString() : textContent;
    }

    private ForwardedEvent unwrapForwardedEvent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return ForwardedEvent.notForwarded(rawContent);
        }

        String trimmed = rawContent.trim();
        if (!trimmed.startsWith("{") || !trimmed.contains("\"type\"") || !trimmed.contains("\"message\"")) {
            return ForwardedEvent.notForwarded(rawContent);
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(trimmed);
            if (!root.has("type") || !root.has("message")) {
                return ForwardedEvent.notForwarded(rawContent);
            }

            JsonNode messageNode = root.get("message");
            JsonNode contentNode = messageNode != null ? messageNode.get("content") : null;
            if (contentNode == null || !contentNode.isArray()) {
                return ForwardedEvent.forwarded("");
            }

            StringBuilder builder = new StringBuilder();
            for (JsonNode block : contentNode) {
                if (block != null && "text".equals(block.path("type").asText())) {
                    String text = block.path("text").asText("");
                    if (!text.isEmpty()) {
                        builder.append(text);
                    }
                }
            }

            return ForwardedEvent.forwarded(builder.toString());
        } catch (Exception e) {
            return ForwardedEvent.notForwarded(rawContent);
        }
    }

    private static class ForwardedEvent {
        private final boolean isForwardedEvent;
        private final String text;

        private ForwardedEvent(boolean isForwardedEvent, String text) {
            this.isForwardedEvent = isForwardedEvent;
            this.text = text;
        }

        static ForwardedEvent forwarded(String text) {
            return new ForwardedEvent(true, text);
        }

        static ForwardedEvent notForwarded(String text) {
            return new ForwardedEvent(false, text);
        }
    }

    private static class StreamEventPayload {
        private final io.agentscope.core.agent.Event event;
        private final String content;
        private final boolean isForwardedEvent;

        private StreamEventPayload(io.agentscope.core.agent.Event event, String content, boolean isForwardedEvent) {
            this.event = event;
            this.content = content;
            this.isForwardedEvent = isForwardedEvent;
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

    /**
     * 判断是否为final_result事件
     */
    protected boolean isFinalResultEvent(EventType eventType) {
        if (eventType == null) {
            return false;
        }
        String eventName = eventType.name();
        return "FINAL_RESULT".equals(eventName) || "FINAL".equals(eventName);
    }
}
