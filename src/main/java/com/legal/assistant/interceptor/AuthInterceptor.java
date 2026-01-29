package com.legal.assistant.interceptor;

import com.legal.assistant.annotation.NoAuth;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    @Autowired
    private JwtUtils jwtUtils;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (RequestMethod.OPTIONS.toString().equals(request.getMethod())){
            return true;
        }
        // 如果不是方法处理器,直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        
        // 检查是否有@NoAuth注解
        if (handlerMethod.hasMethodAnnotation(NoAuth.class) || 
            handlerMethod.getBeanType().isAnnotationPresent(NoAuth.class)) {
            return true;
        }
        
        // 获取Token
        String token = getTokenFromRequest(request);
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_MISSING.getCode(), ErrorCode.TOKEN_MISSING.getMessage());
        }
        
        // 验证Token
        if (!jwtUtils.validateToken(token)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID.getCode(), ErrorCode.TOKEN_INVALID.getMessage());
        }
        
        // 检查Token是否在黑名单中(如用户登出或注销)
        if (Boolean.TRUE.equals(redisTemplate.hasKey("token:blacklist:" + token))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID.getCode(), "Token已失效");
        }
        
        // 将用户ID存储到request中,供后续使用
        Long userId;
        try {
            userId = jwtUtils.getUserIdFromToken(token);
        } catch (Exception e) {
            log.error("解析Token失败", e);
            throw new BusinessException(ErrorCode.TOKEN_INVALID.getCode(), ErrorCode.TOKEN_INVALID.getMessage());
        }
        
        // 检查用户是否已被删除（注销）
        if (Boolean.TRUE.equals(redisTemplate.hasKey("user:deleted:" + userId))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID.getCode(), "用户账户已注销，Token已失效");
        }
        
        // 检查是否因修改手机号需重新登录
        if (Boolean.TRUE.equals(redisTemplate.hasKey("user:relogin:" + userId))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID.getCode(), "手机号已更换，请使用新手机号重新登录");
        }
        
        // 将userId存储到request中
        request.setAttribute("userId", userId);
        
        return true;
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
