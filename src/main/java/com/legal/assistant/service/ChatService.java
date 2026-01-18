package com.legal.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legal.assistant.dto.request.ChatRequest;
import com.legal.assistant.dto.response.StreamChatResponse;
import com.legal.assistant.entity.Conversation;
import com.legal.assistant.entity.Message;
import com.legal.assistant.enums.MessageRole;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.mapper.ConversationMapper;
import com.legal.assistant.mapper.MessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 聊天服务 - 基于AgentScope架构的重构版本
 * 使用AgentRunner执行Agent，支持Flux流式返回
 */
@Slf4j
@Service
public class ChatService {
    
    @Autowired
    private ConversationMapper conversationMapper;
    
    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private ConversationService conversationService;
    
    @Autowired
    private ObjectMapper objectMapper;

    
    /**
     * 处理流式问答请求
     * @param userId 用户ID
     * @param request 请求参数
     * @return 流式响应
     */
    public Flux<StreamChatResponse> processChatStream(Long userId, ChatRequest request) {
        return null;
    }
    
    /**
     * 获取或创建会话
     */
    private Conversation getOrCreateConversation(Long userId, ChatRequest request) {
        Conversation conversation = null;
        if (request.getConversationId() != null) {
            conversation = conversationMapper.selectById(request.getConversationId());
            if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
                throw new BusinessException(404, "会话不存在");
            }
            if (!conversation.getUserId().equals(userId)) {
                throw new BusinessException(403, "无权限访问该会话");
            }
            log.debug("[ChatService] 使用已有会话, conversationId={}", conversation.getId());
        } else {
            // 创建新会话
            conversation = new Conversation();
            conversation.setUserId(userId);
            conversation.setTitle("新会话");
            conversation.setAgentType(request.getAgentType().getCode());
            conversation.setModelType(request.getModelType().getCode());
            conversation.setIsPinned(false);
            conversation.setIsDeleted(false);
            conversation.setCreatedAt(LocalDateTime.now());
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationMapper.insert(conversation);
            log.info("[ChatService] 创建新会话, conversationId={}", conversation.getId());
        }
        return conversation;
    }
    
    /**
     * 保存用户消息
     */
    private Message saveUserMessage(Long conversationId, ChatRequest request) {
        Message userMessage = new Message();
        userMessage.setConversationId(conversationId);
        userMessage.setRole(MessageRole.USER.getCode());
        userMessage.setContent(request.getQuestion());
        userMessage.setStatus("completed");
        userMessage.setCreatedAt(LocalDateTime.now());
        
        // 处理文件ID
        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            try {
                userMessage.setFileIds(objectMapper.writeValueAsString(request.getFileIds()));
            } catch (JsonProcessingException e) {
                log.error("[ChatService] 序列化文件ID失败", e);
            }
        }
        
        // 处理参数
        Map<String, Object> params = new HashMap<>();
        params.put("temperature", request.getTemperature() != null ? request.getTemperature() : 0.7);
        params.put("deepThinking", request.getDeepThinking() != null ? request.getDeepThinking() : false);
        params.put("modelType", request.getModelType().getCode());
        params.put("agentType", request.getAgentType().getCode());
        if (request.getKnowledgeBaseId() != null) {
            params.put("knowledgeBaseId", request.getKnowledgeBaseId());
        }
        try {
            userMessage.setParameters(objectMapper.writeValueAsString(params));
        } catch (JsonProcessingException e) {
            log.error("[ChatService] 序列化参数失败", e);
        }
        
        messageMapper.insert(userMessage);
        return userMessage;
    }
    
    /**
     * 创建AI消息记录
     */
    private Message createAssistantMessage(Long conversationId) {
        Message assistantMessage = new Message();
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole(MessageRole.ASSISTANT.getCode());
        assistantMessage.setStatus("streaming");
        assistantMessage.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(assistantMessage);
        return assistantMessage;
    }
    
    /**
     * 更新AI消息
     */
    private void updateAssistantMessage(Message message, String content, String status) {
        message.setContent(content);
        message.setStatus(status);
        messageMapper.updateById(message);
    }
    
    /**
     * 处理标题生成
     */
    private void handleTitleGeneration(Conversation conversation, ChatRequest request, String response) {
        if (Boolean.TRUE.equals(request.getAutoGenerateTitle()) 
                && "新会话".equals(conversation.getTitle())) {
            String generatedTitle = conversationService.generateTitle(request.getQuestion());
            conversation.setTitle(generatedTitle);
            conversationMapper.updateById(conversation);
            log.debug("[ChatService] 自动生成标题: {}", generatedTitle);
        }
    }
    
    /**
     * 更新会话时间
     */
    private void updateConversationTime(Conversation conversation) {
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }
}
