package com.legal.assistant.service;

import com.legal.assistant.dto.response.ShareDetailResponse;
import com.legal.assistant.dto.response.ShareResponse;
import com.legal.assistant.entity.Message;
import com.legal.assistant.entity.Share;
import com.legal.assistant.entity.User;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.MessageMapper;
import com.legal.assistant.mapper.ShareMapper;
import com.legal.assistant.mapper.UserMapper;
import com.legal.assistant.utils.TimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShareService {
    
    @Autowired
    private ShareMapper shareMapper;
    
    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    /**
     * 创建分享
     */
    @Transactional
    public ShareResponse createShare(Long userId, List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "消息ID列表不能为空");
        }
        
        // 生成分享ID
        String shareId = "share_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        
        // 将消息ID列表转换为逗号分隔的字符串存储
        String messageIdsStr = messageIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        
        Share share = new Share();
        share.setShareId(shareId);
        share.setUserId(userId);
        share.setMessageIds(messageIdsStr);
        share.setViewCount(0);
        share.setCreatedAt(LocalDateTime.now());
        shareMapper.insert(share);
        
        ShareResponse response = new ShareResponse();
        response.setShareId(shareId);
        
        log.info("创建分享: userId={}, shareId={}, messageIds={}", userId, shareId, messageIds);
        return response;
    }
    
    /**
     * 获取分享详情
     */
    public ShareDetailResponse getShareDetail(String shareId) {
        Share share = shareMapper.selectByShareId(shareId);
        if (share == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "分享不存在");
        }
        
        // 增加查看次数
        share.setViewCount(share.getViewCount() + 1);
        shareMapper.updateById(share);
        
        // 获取分享人信息
        User user = userMapper.selectById(share.getUserId());
        String createdBy = user != null ? "user_" + user.getId() : "unknown";
        
        // 解析消息ID列表并获取消息
        List<String> messageIdList = List.of(share.getMessageIds().split(","));
        List<ShareDetailResponse.MessageDTO> messageDTOs = new ArrayList<>();
        
        for (String messageIdStr : messageIdList) {
            try {
                Long msgId = Long.parseLong(messageIdStr.trim());
                Message message = messageMapper.selectById(msgId);
                if (message != null) {
                    ShareDetailResponse.MessageDTO dto = new ShareDetailResponse.MessageDTO();
                    dto.setId(msgId);
                    dto.setQuery(message.getQuery());
                    dto.setAnswer(message.getAnswer());
                    messageDTOs.add(dto);
                }
            } catch (NumberFormatException e) {
                log.warn("无法解析消息ID: {}", messageIdStr);
            }
        }
        
        ShareDetailResponse response = new ShareDetailResponse();
        response.setShareId(shareId);
        response.setCreatedBy(createdBy);
        response.setCreatedAt(TimeUtils.toTimestamp(share.getCreatedAt()));
        response.setMessages(messageDTOs);
        
        return response;
    }
}
