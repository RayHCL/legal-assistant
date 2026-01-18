package com.legal.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.legal.assistant.entity.KnowledgeBase;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.FileMapper;
import com.legal.assistant.mapper.KnowledgeBaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class KnowledgeBaseService {
    
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;
    
    @Autowired
    private FileMapper fileMapper;
    
    /**
     * 创建知识库
     */
    @Transactional
    public KnowledgeBase createKnowledgeBase(Long userId, String name, String description) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setUserId(userId);
        knowledgeBase.setName(name);
        knowledgeBase.setDescription(description);
        knowledgeBase.setFileCount(0);
        knowledgeBase.setCreatedAt(LocalDateTime.now());
        knowledgeBaseMapper.insert(knowledgeBase);
        
        log.info("创建知识库: userId={}, knowledgeBaseId={}", userId, knowledgeBase.getId());
        return knowledgeBase;
    }
    
    /**
     * 获取知识库列表
     */
    public List<KnowledgeBase> getKnowledgeBaseList(Long userId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getUserId, userId)
               .orderByDesc(KnowledgeBase::getCreatedAt);
        return knowledgeBaseMapper.selectList(wrapper);
    }
    
    /**
     * 删除知识库
     */
    @Transactional
    public void deleteKnowledgeBase(Long userId, Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "知识库不存在");
        }
        
        if (!knowledgeBase.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权限删除该知识库");
        }
        
        knowledgeBaseMapper.deleteById(knowledgeBaseId);
        log.info("删除知识库: userId={}, knowledgeBaseId={}", userId, knowledgeBaseId);
    }
}
