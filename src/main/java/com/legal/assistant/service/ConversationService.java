package com.legal.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.legal.assistant.dto.request.ConversationRequest;
import com.legal.assistant.dto.response.ConversationResponse;
import com.legal.assistant.entity.Conversation;
import com.legal.assistant.entity.Message;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.ConversationMapper;
import com.legal.assistant.mapper.MessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConversationService {
    
    @Autowired
    private ConversationMapper conversationMapper;
    
    @Autowired
    private MessageMapper messageMapper;
    
    /**
     * 创建会话
     */
    @Transactional
    public ConversationResponse createConversation(Long userId, ConversationRequest request) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(request.getTitle());
        conversation.setAgentType(request.getAgentType());
        conversation.setModelType(request.getModelType());
        conversation.setIsPinned(false);
        conversation.setIsDeleted(false);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        
        conversationMapper.insert(conversation);
        
        ConversationResponse response = new ConversationResponse();
        BeanUtils.copyProperties(conversation, response);
        response.setMessageCount(0);
        
        log.info("创建会话: userId={}, conversationId={}", userId, conversation.getId());
        return response;
    }
    
    /**
     * 获取会话列表（分页）
     */
    public Page<ConversationResponse> getConversationList(Long userId, Integer page, Integer size) {
        Page<Conversation> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
               .eq(Conversation::getIsDeleted, false)
               .orderByDesc(Conversation::getIsPinned)
               .orderByDesc(Conversation::getUpdatedAt);
        
        Page<Conversation> conversationPage = conversationMapper.selectPage(pageParam, wrapper);
        
        // 转换为响应对象
        Page<ConversationResponse> responsePage = new Page<>(page, size, conversationPage.getTotal());
        List<ConversationResponse> responses = conversationPage.getRecords().stream().map(conv -> {
            ConversationResponse response = new ConversationResponse();
            BeanUtils.copyProperties(conv, response);
            // 获取消息数量
            Integer messageCount = messageMapper.countByConversationId(conv.getId());
            response.setMessageCount(messageCount != null ? messageCount : 0);
            return response;
        }).collect(Collectors.toList());
        responsePage.setRecords(responses);
        
        return responsePage;
    }
    
    /**
     * 获取会话详情
     */
    public ConversationResponse getConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), 
                ErrorCode.CONVERSATION_NOT_FOUND.getMessage());
        }
        
        // 检查权限
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权限访问该会话");
        }
        
        ConversationResponse response = new ConversationResponse();
        BeanUtils.copyProperties(conversation, response);
        Integer messageCount = messageMapper.countByConversationId(conversationId);
        response.setMessageCount(messageCount != null ? messageCount : 0);
        
        return response;
    }
    
    /**
     * 重命名会话
     */
    @Transactional
    public void renameConversation(Long userId, Long conversationId, String newTitle) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), 
                ErrorCode.CONVERSATION_NOT_FOUND.getMessage());
        }
        
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权限操作该会话");
        }
        
        conversation.setTitle(newTitle);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        
        log.info("重命名会话: userId={}, conversationId={}, newTitle={}", userId, conversationId, newTitle);
    }
    
    /**
     * 置顶/取消置顶会话
     */
    @Transactional
    public void pinConversation(Long userId, Long conversationId, Boolean pinned) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), 
                ErrorCode.CONVERSATION_NOT_FOUND.getMessage());
        }
        
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权限操作该会话");
        }
        
        conversation.setIsPinned(pinned);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        
        log.info("{}会话: userId={}, conversationId={}", pinned ? "置顶" : "取消置顶", userId, conversationId);
    }
    
    /**
     * 删除会话（软删除）
     */
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), 
                ErrorCode.CONVERSATION_NOT_FOUND.getMessage());
        }
        
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权限操作该会话");
        }
        
        conversation.setIsDeleted(true);
        conversation.setDeletedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        
        log.info("删除会话: userId={}, conversationId={}", userId, conversationId);
    }
    
    /**
     * 获取会话消息历史
     */
    public List<Message> getMessages(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), 
                ErrorCode.CONVERSATION_NOT_FOUND.getMessage());
        }
        
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权限访问该会话");
        }
        
        return messageMapper.selectByConversationId(conversationId);
    }
    
    /**
     * 自动生成会话标题
     */
    public String generateTitle(String question) {
        // 简化实现：截取问题前30个字符作为标题
        if (question == null || question.isEmpty()) {
            return "新会话";
        }
        if (question.length() <= 30) {
            return question;
        }
        return question.substring(0, 30) + "...";
    }
}
