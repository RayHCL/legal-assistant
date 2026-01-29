package com.legal.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legal.assistant.dto.request.ConversationRequest;
import com.legal.assistant.dto.response.ConversationListResponse;
import com.legal.assistant.dto.response.ConversationResponse;
import com.legal.assistant.entity.Conversation;
import com.legal.assistant.entity.Message;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.ConversationMapper;
import com.legal.assistant.mapper.MessageMapper;
import com.legal.assistant.utils.TimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConversationService {

    @Value("${ai.dashscope.api-key}")
    private String apiKey;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private MessageMapper messageMapper;

    /**
     * 用于缓存异步生成的会话标题
     * key: conversationId, value: CompletableFuture<String>
     */
    private final ConcurrentHashMap<Long, CompletableFuture<String>> titleFutureCache = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DASHSCOPE_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    
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
        response.setCreatedAt(TimeUtils.toTimestamp(conversation.getCreatedAt()));
        response.setUpdatedAt(TimeUtils.toTimestamp(conversation.getUpdatedAt()));
        response.setMessageCount(0);
        
        log.info("创建会话: userId={}, conversationId={}", userId, conversation.getId());
        return response;
    }
    
    /**
     * 获取会话列表（分页）
     */
    public ConversationListResponse getConversationList(Long userId, Integer size, String lastId) {
        // 计算今天的开始和结束时间
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        
        // 查询所有未删除的会话
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
               .eq(Conversation::getIsDeleted, false);
        
        // 如果有lastId，则查询ID小于lastId的记录（用于分页）
        if (lastId != null && !lastId.isEmpty()) {
            try {
                Long lastIdLong = Long.parseLong(lastId);
                wrapper.lt(Conversation::getId, lastIdLong);
            } catch (NumberFormatException e) {
                log.warn("无效的lastId: {}", lastId);
            }
        }
        
        // 按更新时间降序排序
        wrapper.orderByDesc(Conversation::getUpdatedAt);
        
        // 查询size+1条，用于判断是否有下一页
        wrapper.last("LIMIT " + (size + 1));
        List<Conversation> allConversations = conversationMapper.selectList(wrapper);
        
        // 判断是否有下一页
        boolean hasMore = allConversations.size() > size;
        
        // 如果有多余的记录，只取前size条
        if (hasMore) {
            allConversations = allConversations.subList(0, size);
        }
        
        // 分类：置顶、今天、历史，同时转换为响应对象
        List<ConversationResponse> pinnedList = new ArrayList<>();
        List<ConversationResponse> todayList = new ArrayList<>();
        List<ConversationResponse> historyList = new ArrayList<>();
        
        for (Conversation conv : allConversations) {
            // 转换为响应对象
            ConversationResponse response = new ConversationResponse();
            BeanUtils.copyProperties(conv, response);
            response.setCreatedAt(TimeUtils.toTimestamp(conv.getCreatedAt()));
            response.setUpdatedAt(TimeUtils.toTimestamp(conv.getUpdatedAt()));
            // 获取消息数量
            Integer messageCount = messageMapper.countByConversationId(conv.getId());
            response.setMessageCount(messageCount != null ? messageCount : 0);
            
            // 根据置顶状态和更新时间分类
            if (Boolean.TRUE.equals(conv.getIsPinned())) {
                // 置顶的会话
                pinnedList.add(response);
            } else if (conv.getUpdatedAt() != null && 
                      !conv.getUpdatedAt().isBefore(todayStart) && 
                      !conv.getUpdatedAt().isAfter(todayEnd)) {
                // 今天的会话（非置顶）
                todayList.add(response);
            } else {
                // 历史的会话（非置顶）
                historyList.add(response);
            }
        }
        
        // 构建响应
        ConversationListResponse listResponse = new ConversationListResponse();
        listResponse.setPinnedConversations(pinnedList);
        listResponse.setTodayConversations(todayList);
        listResponse.setHistoryConversations(historyList);
        listResponse.setHasMore(hasMore);
        
        // 设置lastId为最后一条记录的ID
        if (!allConversations.isEmpty()) {
            listResponse.setLastId(allConversations.get(allConversations.size() - 1).getId().toString());
        } else {
            listResponse.setLastId(null);
        }
        
        return listResponse;
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
        response.setCreatedAt(TimeUtils.toTimestamp(conversation.getCreatedAt()));
        response.setUpdatedAt(TimeUtils.toTimestamp(conversation.getUpdatedAt()));
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
     * 自动生成会话标题（简单实现，作为临时标题）
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

    /**
     * 异步生成会话标题（调用大模型）
     * 该方法会在后台异步执行，生成完成后自动更新数据库
     *
     * @param conversationId 会话ID
     * @param question       用户问题
     */
    @Async
    public void generateTitleAsync(Long conversationId, String question) {
        if (question == null || question.isEmpty()) {
            log.debug("问题为空，跳过异步标题生成: conversationId={}", conversationId);
            return;
        }

        // 创建 CompletableFuture 并存入缓存
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                log.info("开始异步生成会话标题: conversationId={}", conversationId);

                // 构建提示词
                String truncatedQuestion = question.length() > 200 ? question.substring(0, 200) + "..." : question;
                String prompt = String.format(
                        "请根据以下用户问题，生成一个简洁的会话标题（不超过20个字，不要加引号，直接输出标题）：\n\n用户问题：%s",
                        truncatedQuestion
                );

                // 调用 DashScope API 生成标题
                String generatedTitle = callDashScopeApi(prompt);

                // 清理标题（去除引号、换行等）
                if (generatedTitle != null) {
                    generatedTitle = generatedTitle.trim()
                            .replaceAll("^[\"'\"\"'']+|[\"'\"\"'']+$", "")  // 去除首尾引号
                            .replaceAll("[\\r\\n]+", " ")  // 替换换行为空格
                            .trim();

                    // 限制长度
                    if (generatedTitle.length() > 50) {
                        generatedTitle = generatedTitle.substring(0, 50) + "...";
                    }
                }

                // 如果生成失败，使用简单标题
                if (generatedTitle == null || generatedTitle.isEmpty()) {
                    generatedTitle = generateTitle(question);
                }

                // 更新数据库中的会话标题
                Conversation conversation = conversationMapper.selectById(conversationId);
                if (conversation != null) {
                    conversation.setTitle(generatedTitle);
                    conversation.setUpdatedAt(LocalDateTime.now());
                    conversationMapper.updateById(conversation);
                    log.info("异步更新会话标题完成: conversationId={}, title={}", conversationId, generatedTitle);
                }

                return generatedTitle;

            } catch (Exception e) {
                log.error("异步生成会话标题失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
                // 失败时返回简单标题
                return generateTitle(question);
            } finally {
                // 清理缓存（延迟清理，给获取结果留一些时间）
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(5000);  // 5秒后清理
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    titleFutureCache.remove(conversationId);
                });
            }
        });

        titleFutureCache.put(conversationId, future);
    }

    /**
     * 调用 DashScope API 生成文本
     */
    private String callDashScopeApi(String prompt) {
        try {
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "qwen-turbo");  // 使用较快的模型
            requestBody.put("max_tokens", 50);
            requestBody.put("temperature", 0.3);

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
                        return message.get("content").asText();
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.error("调用 DashScope API 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取异步生成的会话标题
     * 如果标题尚未生成完成，返回null
     *
     * @param conversationId 会话ID
     * @param timeoutMs      等待超时时间（毫秒），0表示不等待
     * @return 生成的标题，如果未完成或不存在返回null
     */
    public String getGeneratedTitle(Long conversationId, long timeoutMs) {
        CompletableFuture<String> future = titleFutureCache.get(conversationId);
        if (future == null) {
            return null;
        }

        try {
            if (timeoutMs <= 0) {
                // 不等待，检查是否已完成
                if (future.isDone()) {
                    return future.get();
                }
                return null;
            } else {
                // 等待指定时间
                return future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } catch (java.util.concurrent.TimeoutException e) {
            log.debug("获取会话标题超时: conversationId={}, timeoutMs={}", conversationId, timeoutMs);
            return null;
        } catch (Exception e) {
            log.warn("获取会话标题失败: conversationId={}, error={}", conversationId, e.getMessage());
            return null;
        }
    }
}
