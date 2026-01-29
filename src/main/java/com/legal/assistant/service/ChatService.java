package com.legal.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legal.assistant.agents.base.ReactLegalAgent;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.agents.factory.LegalAgentFactory;
import com.legal.assistant.dto.request.ChatCompletionRequest;
import com.legal.assistant.dto.response.StreamChatResponse;
import com.legal.assistant.dto.response.SuggestedQuestionsResponse;
import com.legal.assistant.entity.Conversation;
import com.legal.assistant.entity.Message;
import com.legal.assistant.entity.Report;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.ModelType;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.ConversationMapper;
import com.legal.assistant.mapper.MessageMapper;
import com.legal.assistant.mapper.ReportMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.session.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 聊天服务
 */
@Slf4j
@Service
public class ChatService {


    // 管理活跃的流，key 是 conversationId，value 是用于停止流的 Sinks
    private final ConcurrentHashMap<Long, Sinks.Empty<Void>> activeStreams = new ConcurrentHashMap<>();

    // Agent 和 Memory 缓存，key 是 conversationId
    private static class AgentSessionEntry {
        final ReActAgent agent;
        final Memory memory;
        final AgentContext agentContext;
        final LocalDateTime createdAt;

        AgentSessionEntry(ReActAgent agent, Memory memory, AgentContext agentContext) {
            this.agent = agent;
            this.memory = memory;
            this.agentContext = agentContext;
            this.createdAt = LocalDateTime.now();
        }
    }

    private final ConcurrentHashMap<Long, AgentSessionEntry> agentSessionCache = new ConcurrentHashMap<>();

    @Autowired
    private Session agentSession;  // Redis Session

    @Autowired
    private LegalAgentFactory agentFactory;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ReportMapper reportMapper;

    @Value("${ai.dashscope.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String DASHSCOPE_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    /**
     * 创建流式对话
     */
    public Flux<StreamChatResponse> createChatStream(Long userId, ChatCompletionRequest request) {
        Long conversationId = request.getConversationId();
        String generatedTitle = null;
        boolean isNewConversation = false;

        if (conversationId == null) {
            isNewConversation = true;
            // 创建新会话 - 先使用简单标题（临时）
            generatedTitle = conversationService.generateTitle(request.getQuestion());

            Conversation conversation = new Conversation();
            conversation.setUserId(userId);
            conversation.setTitle(generatedTitle);
            conversation.setAgentType(request.getAgentType().name());
            conversation.setModelType(request.getModelType().getCode());
            conversation.setIsPinned(false);
            conversation.setIsDeleted(false);
            conversation.setCreatedAt(LocalDateTime.now());
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.insert(conversation);

            conversationId = conversation.getId();
            log.info("创建新会话: userId={}, conversationId={}", userId, conversationId);

            // 如果需要自动生成标题，启动异步任务调用大模型生成更好的标题
            if (Boolean.TRUE.equals(request.getAutoGenerateTitle())) {
                conversationService.generateTitleAsync(conversationId, request.getQuestion());
                log.info("已启动异步标题生成任务: conversationId={}", conversationId);
            }
        } else {
            // 验证会话权限
            Conversation conversation = conversationMapper.selectById(conversationId);
            if (conversation == null || !conversation.getUserId().equals(userId)) {
                return Flux.error(new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会话不存在或无权限"));
            }
        }

        Long finalConversationId = conversationId;
        String finalGeneratedTitle = generatedTitle;
        boolean finalIsNewConversation = isNewConversation;

        try {
            // 1. 创建消息记录（包含query和answer）
            Message message = new Message();
            message.setConversationId(conversationId);
            message.setQuery(request.getQuestion());
            message.setStatus("streaming");
            message.setCreatedAt(LocalDateTime.now());
            message.setUpdatedAt(LocalDateTime.now());
            messageMapper.insert(message);

            Long messageId = message.getId();

            // 2. 构建完整提示词
            StringBuilder fullPrompt = new StringBuilder(request.getQuestion());

            // 添加可用文件列表提示
            if (!request.getFileIds().isEmpty()) {
                fullPrompt.append("\n\n【可用文件】\n");
                fullPrompt.append("用户提供了以下文件，如需查看文件内容，请使用 getFileContent 工具获取：\n");
                for (Long fileId : request.getFileIds()) {
                    fullPrompt.append("  - 文件ID: ").append(fileId).append("\n");
                }
            }

            // 3. 获取或创建 Agent 会话（带记忆）
            AgentSessionEntry sessionEntry = getOrCreateAgentSession(
                    userId,
                    finalConversationId,
                    request.getAgentType(),
                    request.getModelType(),
                    request.getTemperature()
            );

            final ReActAgent agent = sessionEntry.agent;

            // 4. 创建停止信号
            Sinks.Empty<Void> stopSignal = Sinks.empty();
            activeStreams.put(finalConversationId, stopSignal);

            // 5. 获取Agent实例
            ReactLegalAgent reactAgent = agentFactory.getAgentInstance(request.getAgentType());

            // 6. 用于累积完整内容和报告内容
            StringBuilder contentBuilder = new StringBuilder();
            StringBuilder artifactContentBuilder = new StringBuilder();
            AtomicReference<String> reportIdRef = new AtomicReference<>(null);
            
            // 保存message引用以便在回调中使用
            final Message finalMessage = message;
            final Long finalMessageId = messageId;

            // 7. 执行流式推理
            return reactAgent.streamChat(
                            agent,
                            fullPrompt.toString(),
                            finalMessageId,
                            finalConversationId
                    )
                    .subscribeOn(Schedulers.boundedElastic())
                    .takeUntilOther(Flux.from(stopSignal.asMono()).doOnNext(v -> {
                        log.info("收到停止信号: conversationId={}", finalConversationId);
                    }))
                    .doOnNext(response -> {
                        // 累积完整内容（不落库thinking）
                        if (response != null && response.getContent() != null) {
                            String status = response.getStatus();
                            if ("thinking".equals(status)) {
                                // thinking内容不累积
                                return;
                            } else if ("artifact".equals(status)) {
                                // 报告内容累积到artifactContentBuilder
                                artifactContentBuilder.append(response.getContent());
                                // 同时也累积到contentBuilder，保持流式内容不变
                                contentBuilder.append(response.getContent());
                            } else {
                                // 普通消息累积到contentBuilder
                                contentBuilder.append(response.getContent());
                            }
                        }
                    })
                    .doOnComplete(() -> {
                        // 清理上下文和停止信号
                        activeStreams.remove(finalConversationId);
                        // 保存 Agent 会话记忆
                        saveAgentSession(finalConversationId);

                        // 检查是否有报告内容需要保存
                        String artifactContent = artifactContentBuilder.toString();
                        String answerContent;
                        if (!artifactContent.isEmpty()) {
                            // 生成唯一报告ID
                            String reportId = generateReportId();
                            reportIdRef.set(reportId);

                            // 保存报告到数据库
                            Report report = new Report();
                            report.setReportId(reportId);
                            report.setUserId(userId);
                            report.setConversationId(finalConversationId);
                            report.setMessageId(finalMessageId);
                            report.setFullReportContent(artifactContent);
                            report.setCreatedAt(LocalDateTime.now());
                            report.setUpdatedAt(LocalDateTime.now());
                            reportMapper.insert(report);

                            log.info("保存报告到数据库: reportId={}, conversationId={}, messageId={}, contentLength={}",
                                    reportId, finalConversationId, finalMessageId, artifactContent.length());

                            // 用报告ID替换消息内容
                            answerContent = String.format("[报告ID: %s]", reportId);
                        } else {
                            answerContent = contentBuilder.toString();
                        }

                        // 更新消息answer和状态为完成
                        finalMessage.setAnswer(answerContent);
                        finalMessage.setStatus("completed");
                        finalMessage.setUpdatedAt(LocalDateTime.now());
                        messageMapper.updateById(finalMessage);
                    })
                    .doOnError(error -> {
                        // 清理上下文和停止信号
                        activeStreams.remove(finalConversationId);

                        // 保存 Agent 会话记忆（即使出错也保存）
                        saveAgentSession(finalConversationId);

                        log.error("Agent流式推理失败: conversationId={}", finalConversationId, error);
                        // 更新消息状态为错误
                        finalMessage.setStatus("error");
                        finalMessage.setUpdatedAt(LocalDateTime.now());
                        messageMapper.updateById(finalMessage);
                    })
                    .doOnCancel(() -> {
                        activeStreams.remove(finalConversationId);
                        
                        // 保存 Agent 会话记忆
                        saveAgentSession(finalConversationId);
                        
                        // 更新消息状态为已停止
                        finalMessage.setStatus("stopped");
                        finalMessage.setAnswer(contentBuilder.toString() + "\n\n[已停止生成]");
                        finalMessage.setUpdatedAt(LocalDateTime.now());
                        messageMapper.updateById(finalMessage);
                        
                        log.info("流被取消: conversationId={}, messageId={}", finalConversationId, finalMessageId);
                    })
                    .concatWith(Mono.defer(() -> {
                        // 发送完成响应
                        // 如果是新会话，尝试获取异步生成的标题（等待最多2秒）
                        String titleToReturn = finalGeneratedTitle;
                        if (finalIsNewConversation) {
                            String asyncTitle = conversationService.getGeneratedTitle(finalConversationId, 2000);
                            if (asyncTitle != null && !asyncTitle.isEmpty()) {
                                titleToReturn = asyncTitle;
                                log.info("使用异步生成的标题: conversationId={}, title={}", finalConversationId, asyncTitle);
                            }
                        }
                        return Mono.just(reactAgent.createCompletionResponse(
                                finalMessageId,
                                finalConversationId,
                                titleToReturn,
                                false,
                                null
                        ));
                    }))
                    .onErrorResume(error -> {
                        // 发送错误响应
                        StreamChatResponse errorResponse = reactAgent.createCompletionResponse(
                                finalMessageId,
                                finalConversationId,
                                null,
                                true,
                                error.getMessage()
                        );
                        return Mono.just(errorResponse);
                    });

        } catch (Exception e) {
            log.error("创建流式对话失败: userId={}, conversationId={}", userId, conversationId, e);
            return Flux.error(new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "创建流式对话失败: " + e.getMessage()));
        }
    }

    /**
     * 获取或创建 Agent 会话（带记忆）
     */
    private AgentSessionEntry getOrCreateAgentSession(
            Long userId,
            Long conversationId,
            AgentType agentType,
            ModelType modelType,
            Double temperature) {

        // 尝试从缓存获取
        AgentSessionEntry cachedEntry = agentSessionCache.get(conversationId);
        if (cachedEntry != null) {
            log.info("使用缓存的 Agent 会话，conversationId={}", conversationId);
            return cachedEntry;
        }

        // 创建新的 Agent 会话
        log.info("创建新的 Agent 会话，conversationId={}", conversationId);
        // 创建 Agent 上下文
        AgentContext agentContext = new AgentContext(userId, conversationId);
        // 创建 Agent
        ReActAgent agent = agentFactory.createAgent(agentType, modelType, temperature, agentContext);

        // 获取 Agent 的 Memory
        Memory memory = agent.getMemory();

        // 尝试从 Redis 加载已有会话
        String sessionId = String.valueOf(conversationId);
        if (agent.loadIfExists(agentSession, sessionId)) {
            log.info("从 Redis 加载已有会话: conversationId={}", conversationId);
        }

        // 创建缓存条目
        AgentSessionEntry entry = new AgentSessionEntry(agent, memory, agentContext);

        // 加入缓存
        agentSessionCache.put(conversationId, entry);

        log.info("Agent 会话创建成功，conversationId={}, 记忆消息数={}",
                conversationId, memory.getMessages().size());

        return entry;
    }

    /**
     * 保存 Agent 会话记忆到 Redis
     */
    private void saveAgentSession(Long conversationId) {
        AgentSessionEntry entry = agentSessionCache.get(conversationId);
        if (entry != null && entry.agent != null) {
            try {
                String sessionId = String.valueOf(conversationId);
                entry.agent.saveTo(agentSession, sessionId);
                log.info("保存 Agent 会话到 Redis 成功，conversationId={}", conversationId);
            } catch (Exception e) {
                log.error("保存 Agent 会话到 Redis 失败，conversationId={}", conversationId, e);
            }
        }
    }

    /**
     * 生成唯一的报告ID
     */
    private String generateReportId() {
        // 使用UUID生成唯一ID，并格式化为无连字符的字符串
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 停止流式对话
     */
    public void stopChat(Long userId, Long conversationId) {
        // 验证会话权限
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会话不存在");
        }

        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权限操作该会话");
        }

        // 获取停止信号
        Sinks.Empty<Void> stopSignal = activeStreams.get(conversationId);
        if (stopSignal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "没有活跃的流式对话");
        }

        // 发送停止信号
        stopSignal.tryEmitEmpty();
        
        // 从活跃流中移除
        activeStreams.remove(conversationId);
        
        // 移除 Agent 会话缓存（停止后需要重新创建 Agent）
        AgentSessionEntry entry = agentSessionCache.remove(conversationId);
        if (entry != null) {
            log.info("移除 Agent 会话缓存: conversationId={}", conversationId);
        }
        
        log.info("停止流式对话: userId={}, conversationId={}", userId, conversationId);
    }
    
    /**
     * 检查会话是否有活跃的流式对话
     */
    public boolean hasActiveStream(Long conversationId) {
        return activeStreams.containsKey(conversationId);
    }

    /**
     * 获取下一轮建议问题列表
     */
    public SuggestedQuestionsResponse getSuggestedQuestions(Long userId, Long conversationId) {
        // 1. 验证会话权限
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), 
                    ErrorCode.CONVERSATION_NOT_FOUND.getMessage());
        }

        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权限访问该会话");
        }

        // 2. 获取会话历史消息（最近10条）
        List<Message> messages = messageMapper.selectByConversationId(conversationId);
        if (messages == null || messages.isEmpty()) {
            // 如果没有历史消息，返回空列表
            SuggestedQuestionsResponse response = new SuggestedQuestionsResponse();
            response.setQuestions(new ArrayList<>());
            return response;
        }

        // 只取最近10条消息用于生成建议问题
        List<Message> recentMessages = messages.stream()
                .filter(msg -> msg.getStatus() != null && "completed".equals(msg.getStatus()))
                .limit(10)
                .collect(Collectors.toList());

        if (recentMessages.isEmpty()) {
            SuggestedQuestionsResponse response = new SuggestedQuestionsResponse();
            response.setQuestions(new ArrayList<>());
            return response;
        }

        // 3. 构建对话历史上下文
        StringBuilder conversationContext = new StringBuilder();
        conversationContext.append("以下是用户与AI助手的对话历史：\n\n");
        for (Message msg : recentMessages) {
            if (msg.getQuery() != null && !msg.getQuery().trim().isEmpty()) {
                conversationContext.append("用户：").append(msg.getQuery()).append("\n");
            }
            if (msg.getAnswer() != null && !msg.getAnswer().trim().isEmpty()) {
                // 如果答案是报告ID格式，跳过
                if (!msg.getAnswer().matches("\\[报告ID: .+\\]")) {
                    String answer = msg.getAnswer();
                    // 截取前200字符作为上下文
                    if (answer.length() > 200) {
                        answer = answer.substring(0, 200) + "...";
                    }
                    conversationContext.append("助手：").append(answer).append("\n");
                }
            }
            conversationContext.append("\n");
        }

        // 4. 构建提示词
        String prompt = conversationContext.toString() + 
                "\n请根据以上对话内容，生成3个用户可能想要继续询问的问题。" +
                "问题应该：\n" +
                "1. 与当前对话内容相关\n" +
                "2. 有助于深入理解或解决法律问题\n" +
                "3. 简洁明了，每个问题不超过30个字\n" +
                "4. 以JSON数组格式返回，例如：[\"问题1\", \"问题2\", \"问题3\"]\n" +
                "请直接返回JSON数组，不要包含其他说明文字。";

        // 5. 调用 DashScope API 生成建议问题
        List<String> suggestedQuestions = generateSuggestedQuestions(prompt);

        // 6. 构建响应
        SuggestedQuestionsResponse response = new SuggestedQuestionsResponse();
        response.setQuestions(suggestedQuestions);

        log.info("生成建议问题成功: userId={}, conversationId={}, questionCount={}", 
                userId, conversationId, suggestedQuestions.size());

        return response;
    }

    /**
     * 调用 DashScope API 生成建议问题
     */
    private List<String> generateSuggestedQuestions(String prompt) {
        try {
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "qwen-turbo");  // 使用较快的模型
            requestBody.put("max_tokens", 500);
            requestBody.put("temperature", 0.7);  // 稍微提高温度以获得更多样化的问题

            Map<String, String> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            requestBody.put("messages", List.of(userMessage));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // 调用 API
            String response = restTemplate.postForObject(DASHSCOPE_API_URL, request, String.class);

            // 解析响应
            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).get("message");
                    if (message != null && message.has("content")) {
                        String content = message.get("content").asText().trim();
                        
                        // 尝试解析JSON数组
                        try {
                            // 清理内容，移除可能的markdown代码块标记
                            content = content.replaceAll("```json", "").replaceAll("```", "").trim();
                            
                            JsonNode questionsNode = objectMapper.readTree(content);
                            if (questionsNode.isArray()) {
                                List<String> questions = new ArrayList<>();
                                for (JsonNode questionNode : questionsNode) {
                                    String question = questionNode.asText().trim();
                                    if (!question.isEmpty()) {
                                        questions.add(question);
                                    }
                                }
                                // 限制最多5个问题
                                if (questions.size() > 5) {
                                    questions = questions.subList(0, 5);
                                }
                                return questions;
                            }
                        } catch (Exception e) {
                            log.warn("解析JSON数组失败，尝试按行分割: {}", e.getMessage());
                            // 如果JSON解析失败，尝试按行分割
                            return parseQuestionsFromText(content);
                        }
                    }
                }
            }

            // 如果API调用失败，返回默认问题
            log.warn("生成建议问题失败，返回默认问题");
            return getDefaultQuestions();

        } catch (Exception e) {
            log.error("调用 DashScope API 生成建议问题失败: {}", e.getMessage(), e);
            return getDefaultQuestions();
        }
    }

    /**
     * 从文本中解析问题（备用方案）
     */
    private List<String> parseQuestionsFromText(String text) {
        List<String> questions = new ArrayList<>();
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            // 移除可能的编号和符号
            line = line.replaceAll("^[0-9]+[.、]\\s*", "")
                       .replaceAll("^[-*]\\s*", "")
                       .replaceAll("^\"|\"$", "")
                       .trim();
            if (!line.isEmpty() && line.length() <= 50) {
                questions.add(line);
                if (questions.size() >= 5) {
                    break;
                }
            }
        }
        return questions.isEmpty() ? getDefaultQuestions() : questions;
    }

    /**
     * 获取默认建议问题
     */
    private List<String> getDefaultQuestions() {
        return Arrays.asList(
                "这个问题涉及哪些法律条款？",
                "需要准备哪些证据材料？",
                "有哪些需要注意的风险点？"
        );
    }
}
