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
import io.agentscope.core.message.*;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.GenerateOptions;
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

        return isError
                ? StreamChatResponse.error(messageId, conversationId, errorMessage)
                : StreamChatResponse.completed(messageId, conversationId, generatedTitle);
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
     * 将Event转换为StreamChatResponse（优化版：一次遍历处理所有逻辑）
     */
    protected StreamChatResponse convertEventToResponse(
            Event event,
            Long messageId,
            Long conversationId) {

        Msg msg = event.getMessage();
        if (msg == null) {
            return null;
        }

        List<ContentBlock> contents = msg.getContent();
        if (contents == null || contents.isEmpty()) {
            return null;
        }

        EventType eventType = event.getType();
        StringBuilder textBuilder = null;
        ToolUseBlock toolUseBlock = null;
        ToolResultBlock toolResultBlock = null;

        // 一次遍历，收集所有需要的信息
        for (ContentBlock block : contents) {
            if (block instanceof TextBlock) {
                String text = ((TextBlock) block).getText();
                if (text != null && !text.isEmpty()) {
                    if (textBuilder == null) {
                        textBuilder = new StringBuilder(text);
                    } else {
                        textBuilder.append(text);
                    }
                }
            } else if (block instanceof ThinkingBlock) {
                ThinkingBlock thinkingBlock = (ThinkingBlock) block;
                return StreamChatResponse.message(messageId, conversationId,
                        thinkingBlock.getThinking(), "thinking");
            } else if (block instanceof ToolUseBlock && toolUseBlock == null) {
                toolUseBlock = (ToolUseBlock) block;
            } else if (block instanceof ToolResultBlock && toolResultBlock == null) {
                toolResultBlock = (ToolResultBlock) block;
            }
        }

        // 优先处理文本内容
        if (textBuilder != null && textBuilder.length() > 0) {
            return StreamChatResponse.message(messageId, conversationId, textBuilder.toString(), "message");
        }

        // 处理工具调用
        if (toolUseBlock != null) {
            return StreamChatResponse.toolCall(messageId, conversationId,
                    StreamChatResponse.ToolCallInfo.ofCall(
                            toolUseBlock.getName(),
                            toolUseBlock.getInput() != null ? toolUseBlock.getInput().toString() : null));
        }

        // 处理工具结果
        if (toolResultBlock != null) {
            String resultText = extractToolResultText(toolResultBlock);
            return StreamChatResponse.toolCall(messageId, conversationId,
                    StreamChatResponse.ToolCallInfo.ofResult(toolResultBlock.getId(), resultText));
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
        return sb != null ? sb.toString() : null;
    }

}
