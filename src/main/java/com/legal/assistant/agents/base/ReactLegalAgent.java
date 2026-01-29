package com.legal.assistant.agents.base;

import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.agents.tools.FileToolService;
import com.legal.assistant.dto.response.StreamChatResponse;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
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
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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
                .name(getAgentType().name())
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

        // 创建用户消息
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent(userInput)
                .build();

        // 配置流式选项
        StreamOptions streamOptions = createStreamOptions();

        log.info("开始流式对话: agentType={}, messageId={}, conversationId={}",
                getAgentType().name(), messageId, conversationId);

        // 执行流式推理并转换为 StreamChatResponse
        return agent.stream(userMsg, streamOptions)
                .filter(event -> event != null && event.getMessage() != null)
                .flatMap(event -> Mono.justOrEmpty(convertEventToResponse(event, messageId, conversationId)))
                .filter(response -> response.getContent() != null && !response.getContent().isEmpty())
                .doOnError(error -> log.error("流式对话错误: agentType={}, messageId={}, error={}",
                        getAgentType().name(), messageId, error.getMessage()))
                .doOnComplete(() -> log.info("流式对话完成: agentType={}, messageId={}",
                        getAgentType().name(), messageId));
    }

    /**
     * 创建流式选项
     * 子类可以覆盖此方法来自定义流式选项
     */
    protected StreamOptions createStreamOptions() {
        return StreamOptions.builder()
                .eventTypes(EventType.ALL)
                .incremental(true)
                .includeReasoningResult(true)
                .includeActingChunk(true)
                .build();
    }

    /**
     * 将Event转换为StreamChatResponse
     */
    protected StreamChatResponse convertEventToResponse(
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
            // 检查是否有工具调用信息
            StreamChatResponse.ToolCallInfo toolCallInfo = extractToolCallInfo(msg, event.getType());
            if (toolCallInfo != null) {
                return new StreamChatResponse(
                        messageId,
                        conversationId,
                        "",
                        toolCallInfo.getIsToolCall() ? "tool_call" : "tool_result",
                        null,
                        false,
                        toolCallInfo
                );
            }
            return null;
        }

        // 确定状态
        String status = determineStreamStatus(event, content);

        return new StreamChatResponse(
                messageId,
                conversationId,
                content,
                status,
                null,
                false,
                null
        );
    }

    /**
     * 确定流式响应的状态
     * 子类可以覆盖此方法来自定义状态判断逻辑
     *
     * @param event   事件
     * @param content 内容
     * @return 状态字符串
     */
    protected String determineStreamStatus(Event event, String content) {
        EventType eventType = event.getType();

        if (eventType == EventType.REASONING) {
            // 检查是否是 thinking 内容（深度思考）
            if (isThinkingContent(event)) {
                return "thinking";
            }
            return "message";
        } else if (eventType == EventType.TOOL_RESULT) {
            return "tool_result";
        }

        // 检查消息内容是否包含工具调用
        Msg msg = event.getMessage();
        if (msg != null && msg.getContent() != null) {
            for (ContentBlock block : msg.getContent()) {
                if (block instanceof ToolUseBlock) {
                    return "tool_call";
                }
            }
        }

        // 默认为普通消息
        return "message";
    }

    /**
     * 判断是否是 thinking 内容（深度思考）
     */
    protected boolean isThinkingContent(Event event) {
        Msg msg = event.getMessage();
        if (msg == null) {
            return false;
        }

        // 检查消息内容块是否是 thinking 类型
        List<ContentBlock> contents = msg.getContent();
        if (contents != null) {
            for (ContentBlock block : contents) {
                // 检查是否是 ThinkingBlock 类型
                if (block.getClass().getSimpleName().equals("ThinkingBlock")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 用于子类覆盖的状态确定方法（兼容旧版本）
     */
    protected String determineStreamStatus(String chunkText, String fullText, int[] reportState) {
        return "message";
    }

    /**
     * 从消息中提取文本内容
     */
    protected String getTextContent(Msg msg) {
        if (msg == null) {
            return null;
        }

        List<ContentBlock> contents = msg.getContent();
        if (contents == null || contents.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : contents) {
            if (block instanceof TextBlock) {
                String text = ((TextBlock) block).getText();
                if (text != null) {
                    sb.append(text);
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 提取工具调用信息
     */
    protected StreamChatResponse.ToolCallInfo extractToolCallInfo(Msg msg, EventType eventType) {
        if (msg == null) {
            return null;
        }

        List<ContentBlock> contents = msg.getContent();
        if (contents == null || contents.isEmpty()) {
            return null;
        }

        for (ContentBlock block : contents) {
            if (block instanceof ToolUseBlock) {
                ToolUseBlock toolUse = (ToolUseBlock) block;
                return new StreamChatResponse.ToolCallInfo(
                        toolUse.getName(),
                        toolUse.getInput() != null ? toolUse.getInput().toString() : null,
                        null,
                        true,
                        false
                );
            } else if (block instanceof ToolResultBlock) {
                ToolResultBlock toolResult = (ToolResultBlock) block;
                String resultText = extractToolResultText(toolResult);
                return new StreamChatResponse.ToolCallInfo(
                        toolResult.getId(),  // 使用 getId() 方法
                        null,
                        resultText,
                        false,
                        true
                );
            }
        }
        return null;
    }

    /**
     * 提取工具结果文本
     */
    private String extractToolResultText(ToolResultBlock toolResult) {
        List<ContentBlock> output = toolResult.getOutput();
        if (output == null || output.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : output) {
            if (block instanceof TextBlock) {
                String text = ((TextBlock) block).getText();
                if (text != null) {
                    sb.append(text);
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

}
