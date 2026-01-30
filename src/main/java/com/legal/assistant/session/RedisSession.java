package com.legal.assistant.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.agentscope.core.session.Session;
import io.agentscope.core.state.SessionKey;
import io.agentscope.core.state.SimpleSessionKey;
import io.agentscope.core.state.State;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的 AgentScope Session 实现
 * 用于持久化存储 Agent 状态和记忆
 */
@Slf4j
public class RedisSession implements Session {

    private static final String KEY_PREFIX = "agentscope:session:";
    private static final String KEYS_SUFFIX = ":_keys";
    private static final String LIST_SUFFIX = ":list";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long expireSeconds;

    /**
     * 创建 RedisSession
     *
     * @param redisTemplate Spring Redis 模板
     */
    public RedisSession(StringRedisTemplate redisTemplate) {
        this(redisTemplate, 7 * 24 * 3600); // 默认7天过期
    }

    /**
     * 创建 RedisSession
     *
     * @param redisTemplate Spring Redis 模板
     * @param expireSeconds 过期时间（秒），0表示永不过期
     */
    public RedisSession(StringRedisTemplate redisTemplate, long expireSeconds) {
        this.redisTemplate = redisTemplate;
        this.expireSeconds = expireSeconds;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 从 SessionKey 获取 session ID
     */
    private String getSessionId(SessionKey sessionKey) {
        if (sessionKey instanceof SimpleSessionKey) {
            return ((SimpleSessionKey) sessionKey).sessionId();
        }
        // 回退到 toString()
        return sessionKey.toString();
    }

    /**
     * 获取 Redis key
     */
    private String getKey(SessionKey sessionKey, String stateKey) {
        return KEY_PREFIX + getSessionId(sessionKey) + ":" + stateKey;
    }

    /**
     * 获取会话的 keys 索引 key
     */
    private String getKeysIndexKey(SessionKey sessionKey) {
        return KEY_PREFIX + getSessionId(sessionKey) + KEYS_SUFFIX;
    }

    /**
     * 设置过期时间
     */
    private void setExpire(String key) {
        if (expireSeconds > 0) {
            redisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public void save(SessionKey sessionKey, String key, State value) {
        try {
            String redisKey = getKey(sessionKey, key);
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(redisKey, json);
            setExpire(redisKey);

            // 添加到 keys 索引
            String keysIndexKey = getKeysIndexKey(sessionKey);
            redisTemplate.opsForSet().add(keysIndexKey, key);
            setExpire(keysIndexKey);

            log.debug("保存状态: sessionKey={}, key={}", getSessionId(sessionKey), key);
        } catch (JsonProcessingException e) {
            log.error("序列化状态失败: sessionKey={}, key={}", getSessionId(sessionKey), key, e);
            throw new RuntimeException("Failed to serialize state", e);
        }
    }

    @Override
    public void save(SessionKey sessionKey, String key, List<? extends State> values) {
        String redisKey = getKey(sessionKey, key) + LIST_SUFFIX;

        // 先删除旧列表
        redisTemplate.delete(redisKey);

        if (values == null || values.isEmpty()) {
            return;
        }

        try {
            // 将每个值序列化并添加到列表
            for (State value : values) {
                String json = objectMapper.writeValueAsString(value);
                redisTemplate.opsForList().rightPush(redisKey, json);
            }
            setExpire(redisKey);

            // 添加到 keys 索引
            String keysIndexKey = getKeysIndexKey(sessionKey);
            redisTemplate.opsForSet().add(keysIndexKey, key + LIST_SUFFIX);
            setExpire(keysIndexKey);

            log.debug("保存状态列表: sessionKey={}, key={}, size={}", getSessionId(sessionKey), key, values.size());
        } catch (JsonProcessingException e) {
            log.error("序列化状态列表失败: sessionKey={}, key={}", getSessionId(sessionKey), key, e);
            throw new RuntimeException("Failed to serialize state list", e);
        }
    }

    @Override
    public <T extends State> Optional<T> get(SessionKey sessionKey, String key, Class<T> type) {
        String redisKey = getKey(sessionKey, key);
        String json = redisTemplate.opsForValue().get(redisKey);

        if (json == null || json.isEmpty()) {
            return Optional.empty();
        }

        try {
            T value = objectMapper.readValue(json, type);
            log.debug("获取状态: sessionKey={}, key={}", getSessionId(sessionKey), key);
            return Optional.of(value);
        } catch (JsonProcessingException e) {
            log.error("反序列化状态失败: sessionKey={}, key={}", getSessionId(sessionKey), key, e);
            return Optional.empty();
        }
    }

    @Override
    public <T extends State> List<T> getList(SessionKey sessionKey, String key, Class<T> itemType) {
        String redisKey = getKey(sessionKey, key) + LIST_SUFFIX;
        List<String> jsonList = redisTemplate.opsForList().range(redisKey, 0, -1);

        if (jsonList == null || jsonList.isEmpty()) {
            return new ArrayList<>();
        }

        List<T> result = new ArrayList<>();
        for (String json : jsonList) {
            try {
                T value = objectMapper.readValue(json, itemType);
                result.add(value);
            } catch (JsonProcessingException e) {
                log.error("反序列化状态列表项失败: sessionKey={}, key={}", getSessionId(sessionKey), key, e);
            }
        }

        log.debug("获取状态列表: sessionKey={}, key={}, size={}", getSessionId(sessionKey), key, result.size());
        return result;
    }

    @Override
    public boolean exists(SessionKey sessionKey) {
        String keysIndexKey = getKeysIndexKey(sessionKey);
        Long size = redisTemplate.opsForSet().size(keysIndexKey);
        return size != null && size > 0;
    }

    @Override
    public void delete(SessionKey sessionKey) {
        String keysIndexKey = getKeysIndexKey(sessionKey);
        Set<String> keys = redisTemplate.opsForSet().members(keysIndexKey);

        if (keys != null && !keys.isEmpty()) {
            // 删除所有状态 key
            for (String key : keys) {
                String redisKey = getKey(sessionKey, key);
                redisTemplate.delete(redisKey);
            }
        }

        // 删除 keys 索引
        redisTemplate.delete(keysIndexKey);

        log.info("删除会话: sessionKey={}", getSessionId(sessionKey));
    }

    @Override
    public Set<SessionKey> listSessionKeys() {
        Set<String> redisKeys = redisTemplate.keys(KEY_PREFIX + "*" + KEYS_SUFFIX);

        if (redisKeys == null || redisKeys.isEmpty()) {
            return Collections.emptySet();
        }

        return redisKeys.stream()
                .map(key -> {
                    // 从 key 中提取 sessionId
                    String sessionId = key.substring(KEY_PREFIX.length(), key.length() - KEYS_SUFFIX.length());
                    return SimpleSessionKey.of(sessionId);
                })
                .collect(Collectors.toSet());
    }

    @Override
    public void close() {
        // Redis 连接由 Spring 管理，无需手动关闭
        log.debug("RedisSession closed");
    }
}
