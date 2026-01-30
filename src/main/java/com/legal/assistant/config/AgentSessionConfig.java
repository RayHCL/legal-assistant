package com.legal.assistant.config;

import com.legal.assistant.session.RedisSession;
import io.agentscope.core.session.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Agent 会话配置
 */
@Configuration
public class AgentSessionConfig {

    @Value("${agent.session.expire-days:7}")
    private int expireDays;

    /**
     * 创建基于 Redis 的 Session Bean
     */
    @Bean
    public Session agentSession(StringRedisTemplate redisTemplate) {
        long expireSeconds = expireDays * 24 * 3600L;
        return new RedisSession(redisTemplate, expireSeconds);
    }
}
