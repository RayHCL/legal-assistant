package com.legal.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legal.assistant.agents.factory.LegalAgentFactory;
import com.legal.assistant.dto.request.ChatCompletionRequest;
import com.legal.assistant.dto.response.StreamChatResponse;
import com.legal.assistant.entity.Conversation;
import com.legal.assistant.entity.Message;
import com.legal.assistant.enums.MessageRole;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.ConversationMapper;
import com.legal.assistant.mapper.MessageMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

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
    public Flux<String> createChatStream(Long userId, ChatCompletionRequest request) {
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

//            // 2. 检索知识库
//            List<String> knowledgeContents = new ArrayList<>();
//            if (request.getKnowledgeBaseIds() != null && !request.getKnowledgeBaseIds().isEmpty()) {
//                knowledgeContents = knowledgeRetriever.retrieve(
//                        request.getKnowledgeBaseIds(),
//                        request.getQuestion(),
//                        5
//                );
//            }
//
//            // 3. 获取文件内容
//            List<String> fileContents = new ArrayList<>();
//            if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
//                for (Long fileId : request.getFileIds()) {
//                    String content = knowledgeRetriever.getFileTextContent(fileId);
//                    if (content != null && !content.isEmpty()) {
//                        fileContents.add(content);
//                    }
//                }
//            }

            // 4. 构建完整提示词
            StringBuilder fullPrompt = new StringBuilder(request.getQuestion());

//            // 添加知识库内容
//            if (!knowledgeContents.isEmpty()) {
//                fullPrompt.append("\n\n【知识库内容】\n");
//                for (int i = 0; i < knowledgeContents.size(); i++) {
//                    fullPrompt.append("参考内容").append(i + 1).append("：\n").append(knowledgeContents.get(i)).append("\n");
//                }
//            }
//
//            // 添加文件内容
//            if (!fileContents.isEmpty()) {
//                fullPrompt.append("\n\n【文件内容】\n");
//                for (int i = 0; i < fileContents.size(); i++) {
//                    fullPrompt.append("文件").append(i + 1).append("：\n").append(fileContents.get(i)).append("\n");
//                }
//            }

            // 5. 创建助手消息记录
            Message assistantMessage = new Message();
            assistantMessage.setConversationId(conversationId);
            assistantMessage.setRole(MessageRole.ASSISTANT.getCode());
            assistantMessage.setStatus("streaming");
            assistantMessage.setCreatedAt(LocalDateTime.now());
            messageMapper.insert(assistantMessage);

            Long finalAssistantMessageId = assistantMessage.getId();

            // 6. 创建Agent
            ReActAgent agent = agentFactory.createAgent(
                    request.getAgentType(),
                    request.getModelType(),
                    request.getTemperature()
            );

            // 7. 配置流式输出选项
            StreamOptions streamOptions = StreamOptions.builder()
                    .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                    .incremental(true)
                    .includeReasoningResult(false)
                    .build();

            // 8. 创建输入消息
            Msg inputMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent(fullPrompt.toString())
                    .build();

            // 9. 用于累积完整内容
            StringBuilder contentBuilder = new StringBuilder();

            // 10. 创建停止信号
            Sinks.Empty<Void> stopSignal = Sinks.empty();
            activeStreams.put(finalConversationId, stopSignal);

            // 11. 执行流式推理
            return Flux.create(emitter -> {
                // 获取停止信号
                Sinks.Empty<Void> currentStopSignal = activeStreams.get(finalConversationId);

                agent.stream(inputMsg, streamOptions)
                        .subscribeOn(Schedulers.boundedElastic())
                        .takeUntilOther(Flux.from(currentStopSignal.asMono()).doOnNext(v -> {
                            log.info("收到停止信号: conversationId={}", finalConversationId);
                        }))
                        .doOnComplete(() -> {
                            // 清理停止信号
                            activeStreams.remove(finalConversationId);

                            // 更新消息状态为完成
                            assistantMessage.setContent(contentBuilder.toString());
                            assistantMessage.setStatus("completed");
                            messageMapper.updateById(assistantMessage);

                            // 发送完成事件
                            try {
                                StreamChatResponse response = new StreamChatResponse(
                                        finalAssistantMessageId,
                                        finalConversationId,
                                        "",
                                        "completed",
                                        finalGeneratedTitle,
                                        true
                                );
                                emitter.next("data: " + objectMapper.writeValueAsString(response) + "\n\n");
                                emitter.complete();
                            } catch (Exception e) {
                                log.error("发送完成事件失败", e);
                                emitter.error(e);
                            }
                        })
                        .doOnError(error -> {
                            // 清理停止信号
                            activeStreams.remove(finalConversationId);

                            log.error("Agent流式推理失败: conversationId={}", finalConversationId, error);
                            try {
                                // 更新消息状态为错误
                                assistantMessage.setStatus("error");
                                messageMapper.updateById(assistantMessage);

                                StreamChatResponse response = new StreamChatResponse(
                                        finalAssistantMessageId,
                                        finalConversationId,
                                        error.getMessage(),
                                        "error",
                                        null,
                                        true
                                );
                                emitter.next("data: " + objectMapper.writeValueAsString(response) + "\n\n");
                                emitter.complete();
                            } catch (Exception e) {
                                log.error("发送错误事件失败", e);
                                emitter.error(error);
                            }
                        })
                        .doOnCancel(() -> {
                            // 清理停止信号
                            activeStreams.remove(finalConversationId);
                            log.info("流被取消: conversationId={}", finalConversationId);
                        })
                        .subscribe(event -> {
                            try {
                                Msg message = event.getMessage();
                                if (message != null) {
                                    String chunkText = message.getTextContent();

                                    if (chunkText != null && !chunkText.isEmpty()) {
                                        // 累积完整内容
                                        contentBuilder.append(chunkText);

                                        // 发送流式内容事件
                                        StreamChatResponse response = new StreamChatResponse(
                                                finalAssistantMessageId,
                                                finalConversationId,
                                                chunkText,
                                                "streaming",
                                                null,
                                                false
                                        );
                                        emitter.next("data: " + objectMapper.writeValueAsString(response) + "\n\n");
                                    }
                                }
                            } catch (Exception e) {
                                log.error("处理流式事件失败", e);
                            }
                        });
            });

        } catch (Exception e) {
            log.error("创建流式对话失败: userId={}, conversationId={}", userId, conversationId, e);
            return Flux.error(new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "创建流式对话失败: " + e.getMessage()));
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
