package com.legal.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legal.assistant.agents.base.ReactLegalAgent;
import com.legal.assistant.agents.context.AgentContext;
import com.legal.assistant.agents.factory.LegalAgentFactory;
import com.legal.assistant.dto.request.ChatCompletionRequest;
import com.legal.assistant.dto.response.StreamChatResponse;
import com.legal.assistant.entity.Conversation;
import com.legal.assistant.entity.Message;
import com.legal.assistant.enums.AgentType;
import com.legal.assistant.enums.MessageRole;
import com.legal.assistant.enums.ModelType;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.ConversationMapper;
import com.legal.assistant.mapper.MessageMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天服务
 */
@Slf4j
@Service
public class ChatService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 管理活跃的流，key 是 conversationId，value 是用于停止流的 Sinks
    private final ConcurrentHashMap<Long, Sinks.Empty<Void>> activeStreams = new ConcurrentHashMap<>();

    // Agent 和 Memory 缓存，key 是 conversationId
    private static class AgentSessionEntry {
        final ReActAgent agent;
        final Memory memory;
        final SessionManager sessionManager;
        final LocalDateTime createdAt;

        AgentSessionEntry(ReActAgent agent, Memory memory, SessionManager sessionManager) {
            this.agent = agent;
            this.memory = memory;
            this.sessionManager = sessionManager;
            this.createdAt = LocalDateTime.now();
        }
    }

    private final ConcurrentHashMap<Long, AgentSessionEntry> agentSessionCache = new ConcurrentHashMap<>();

    @Value("${agent.session.base-path:./sessions}")
    private String sessionBasePath;

    @Autowired
    private LegalAgentFactory agentFactory;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;




    /**
     * 创建流式对话
     */
    public Flux<StreamChatResponse> createChatStream(Long userId, ChatCompletionRequest request) {
        Long conversationId = request.getConversationId();
        String generatedTitle = null;

        if (conversationId == null) {
            // 创建新会话
            generatedTitle = Boolean.TRUE.equals(request.getAutoGenerateTitle()) ?
                    conversationService.generateTitle(request.getQuestion()) : "新会话";

            Conversation conversation = new Conversation();
            conversation.setUserId(userId);
            conversation.setTitle(generatedTitle);
            conversation.setAgentType(request.getAgentType().getCode());
            conversation.setModelType(request.getModelType().getCode());
            conversation.setIsPinned(false);
            conversation.setIsDeleted(false);
            conversation.setCreatedAt(LocalDateTime.now());
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.insert(conversation);

            conversationId = conversation.getId();
            log.info("创建新会话: userId={}, conversationId={}", userId, conversationId);
        } else {
            // 验证会话权限
            Conversation conversation = conversationMapper.selectById(conversationId);
            if (conversation == null || !conversation.getUserId().equals(userId)) {
                return Flux.error(new BusinessException(ErrorCode.NOT_FOUND.getCode(), "会话不存在或无权限"));
            }
        }

        Long finalConversationId = conversationId;
        String finalGeneratedTitle = generatedTitle;

        try {
            // 1. 保存用户消息
            Message userMessage = new Message();
            userMessage.setConversationId(conversationId);
            userMessage.setRole(MessageRole.USER.getCode());
            userMessage.setContent(request.getQuestion());
            userMessage.setStatus("completed");
            userMessage.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(userMessage);


            // 4. 构建完整提示词
            StringBuilder fullPrompt = new StringBuilder(request.getQuestion());

            // 添加可用文件列表提示
            if (!request.getFileIds().isEmpty()) {
                fullPrompt.append("\n\n【可用文件】\n");
                fullPrompt.append("用户提供了以下文件，如需查看文件内容，请使用 getFileContent 工具获取：\n");
                for (Long fileId : request.getFileIds()) {
                    fullPrompt.append("  - 文件ID: ").append(fileId).append("\n");
                }
            }

            // 5. 创建助手消息记录
            Message assistantMessage = new Message();
            assistantMessage.setConversationId(conversationId);
            assistantMessage.setRole(MessageRole.ASSISTANT.getCode());
            assistantMessage.setStatus("streaming");
            assistantMessage.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(assistantMessage);

            Long finalAssistantMessageId = assistantMessage.getId();

            // 6. 获取或创建 Agent 会话（带记忆）
            AgentSessionEntry sessionEntry = getOrCreateAgentSession(
                    userId,
                    finalConversationId,
                    request.getAgentType(),
                    request.getModelType(),
                    request.getTemperature()
            );

            final ReActAgent agent = sessionEntry.agent;
            final Memory memory = sessionEntry.memory;
            final SessionManager sessionManager = sessionEntry.sessionManager;

            // 7. 创建停止信号
            Sinks.Empty<Void> stopSignal = Sinks.empty();
            activeStreams.put(finalConversationId, stopSignal);

            // 8. 获取Agent实例
            ReactLegalAgent reactAgent = agentFactory.getAgentInstance(request.getAgentType());

            // 9. 用于累积完整内容
            StringBuilder contentBuilder = new StringBuilder();

            // 10. 执行流式推理
            return reactAgent.streamChat(
                            agent,
                            fullPrompt.toString(),
                            finalAssistantMessageId,
                            finalConversationId
                    )
                    .subscribeOn(Schedulers.boundedElastic())
                    .takeUntilOther(Flux.from(stopSignal.asMono()).doOnNext(v -> {
                        log.info("收到停止信号: conversationId={}", finalConversationId);
                    }))
                    .doOnNext(response -> {
                        // 累积完整内容
                        if (response != null && response.getContent() != null) {
                            contentBuilder.append(response.getContent());
                        }
                    })
                    .doOnComplete(() -> {
                        // 清理上下文和停止信号
                        activeStreams.remove(finalConversationId);
                        // 保存 Agent 会话记忆
                        saveAgentSession(finalConversationId);

                        // 更新消息状态为完成
                        assistantMessage.setContent(contentBuilder.toString());
                        assistantMessage.setStatus("completed");
                        messageMapper.updateById(assistantMessage);
                    })
                    .doOnError(error -> {
                        // 清理上下文和停止信号
                        activeStreams.remove(finalConversationId);

                        // 保存 Agent 会话记忆（即使出错也保存）
                        saveAgentSession(finalConversationId);

                        log.error("Agent流式推理失败: conversationId={}", finalConversationId, error);
                        // 更新消息状态为错误
                        assistantMessage.setStatus("error");
                        messageMapper.updateById(assistantMessage);
                    })
                    .doOnCancel(() -> {
                        activeStreams.remove(finalConversationId);
                        log.info("流被取消: conversationId={}", finalConversationId);
                    })
                    .concatWith(Mono.defer(() -> {
                        // 发送完成响应
                        return Mono.just(reactAgent.createCompletionResponse(
                                finalAssistantMessageId,
                                finalConversationId,
                                finalGeneratedTitle,
                                false,
                                null
                        ));
                    }))
                    .onErrorResume(error -> {
                        // 发送错误响应
                        StreamChatResponse errorResponse = reactAgent.createCompletionResponse(
                                finalAssistantMessageId,
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

        // 创建新的 Agent 和 SessionManager
        log.info("创建新的 Agent 会话，conversationId={}", conversationId);
        // 创建 Agent 上下文
        AgentContext agentContext = new AgentContext(userId, conversationId);
        // 创建 Agent
        ReActAgent agent = agentFactory.createAgent(agentType, modelType, temperature, agentContext);

        // 获取 Agent 的 Memory
        Memory memory = agent.getMemory();

        // 创建会话目录
        File sessionDir = new File(sessionBasePath, String.valueOf(conversationId));
        sessionDir.mkdirs();

        // 创建 SessionManager
        SessionManager sessionManager = SessionManager.forSessionId(String.valueOf(conversationId))
                .withSession(new JsonSession(sessionDir.toPath()))
                .addComponent(agent)
                .addComponent(memory);

        // 尝试加载已有会话
        sessionManager.loadIfExists();

        // 创建缓存条目
        AgentSessionEntry entry = new AgentSessionEntry(agent, memory, sessionManager);

        // 加入缓存
        agentSessionCache.put(conversationId, entry);

        log.info("Agent 会话创建成功，conversationId={}, 记忆消息数={}",
                conversationId, memory.getMessages().size());

        return entry;
    }

    /**
     * 保存 Agent 会话记忆
     */
    private void saveAgentSession(Long conversationId) {
        AgentSessionEntry entry = agentSessionCache.get(conversationId);
        if (entry != null && entry.sessionManager != null) {
            try {
                entry.sessionManager.saveSession();
                log.info("保存 Agent 会话成功，conversationId={}", conversationId);
            } catch (Exception e) {
                log.error("保存 Agent 会话失败，conversationId={}", conversationId, e);
            }
        }
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
        log.info("停止流式对话: userId={}, conversationId={}", userId, conversationId);
    }
}
