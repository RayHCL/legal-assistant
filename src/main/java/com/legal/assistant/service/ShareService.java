package com.legal.assistant.service;

import com.legal.assistant.dto.response.ConversationResponse;
import com.legal.assistant.entity.Conversation;
import com.legal.assistant.entity.Message;
import com.legal.assistant.entity.Share;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.ConversationMapper;
import com.legal.assistant.mapper.MessageMapper;
import com.legal.assistant.mapper.ShareMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ShareService {
    
    @Autowired
    private ShareMapper shareMapper;
    
    @Autowired
    private ConversationMapper conversationMapper;
    
    @Autowired
    private MessageMapper messageMapper;
    
    @Value("${business.share.default-expiration-days:7}")
    private Integer defaultExpirationDays;
    
    /**
     * 创建分享
     */
    @Transactional
    public Map<String, Object> createShare(Long userId, Long conversationId, Integer expirationDays, String password) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || Boolean.TRUE.equals(conversation.getIsDeleted())) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND.getCode(), 
                ErrorCode.CONVERSATION_NOT_FOUND.getMessage());
        }
        
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权限分享该会话");
        }
        
        // 生成分享ID
        String shareId = UUID.randomUUID().toString().replace("-", "");
        
        // 计算过期时间
        int days = expirationDays != null ? expirationDays : defaultExpirationDays;
        LocalDateTime expirationTime = LocalDateTime.now().plusDays(days);
        
        // 处理密码
        String passwordHash = null;
        if (password != null && !password.isEmpty()) {
            passwordHash = DigestUtils.md5DigestAsHex(password.getBytes());
        }
        
        Share share = new Share();
        share.setConversationId(conversationId);
        share.setShareId(shareId);
        share.setPasswordHash(passwordHash);
        share.setExpirationTime(expirationTime);
        share.setViewCount(0);
        share.setCreatedAt(LocalDateTime.now());
        shareMapper.insert(share);
        
        Map<String, Object> result = new HashMap<>();
        result.put("shareId", shareId);
        result.put("shareUrl", "/api/share/" + shareId);
        result.put("expirationTime", expirationTime);
        result.put("hasPassword", passwordHash != null);
        
        log.info("创建分享: userId={}, conversationId={}, shareId={}", userId, conversationId, shareId);
        return result;
    }
    
    /**
     * 访问分享（验证密码）
     */
    public Map<String, Object> accessShare(String shareId, String password) {
        Share share = shareMapper.selectByShareId(shareId);
        if (share == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "分享不存在");
        }
        
        // 检查是否过期
        if (share.getExpirationTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "分享已过期");
        }
        
        // 验证密码
        if (share.getPasswordHash() != null) {
            if (password == null || password.isEmpty()) {
                throw new BusinessException(400, "需要密码");
            }
            String inputHash = DigestUtils.md5DigestAsHex(password.getBytes());
            if (!share.getPasswordHash().equals(inputHash)) {
                throw new BusinessException(400, "密码错误");
            }
        }
        
        // 增加查看次数
        share.setViewCount(share.getViewCount() + 1);
        shareMapper.updateById(share);
        
        // 获取会话和消息
        Conversation conversation = conversationMapper.selectById(share.getConversationId());
        List<Message> messages = messageMapper.selectByConversationId(share.getConversationId());
        
        Map<String, Object> result = new HashMap<>();
        result.put("conversation", conversation);
        result.put("messages", messages);
        
        return result;
    }
}
