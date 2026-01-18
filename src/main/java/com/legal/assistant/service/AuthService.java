package com.legal.assistant.service;

import com.legal.assistant.dto.request.LoginRequest;
import com.legal.assistant.dto.response.LoginResponse;
import com.legal.assistant.entity.User;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.UserMapper;
import com.legal.assistant.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthService {
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private SmsService smsService;
    
    @Autowired
    private JwtUtils jwtUtils;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Value("${sms.send-limit-per-minute:1}")
    private Integer sendLimitPerMinute;
    
    @Value("${sms.send-limit-per-day:10}")
    private Integer sendLimitPerDay;
    
    @Value("${business.user.default-nickname-prefix:用户}")
    private String defaultNicknamePrefix;
    
    /**
     * 发送验证码
     */
    public void sendCode(String phone) {
        // 检查发送频率限制 - 每分钟
        String minuteLimitKey = "sms:limit:minute:" + phone;
        String minuteCount = redisTemplate.opsForValue().get(minuteLimitKey);
        if (minuteCount != null && Integer.parseInt(minuteCount) >= sendLimitPerMinute) {
            throw new BusinessException(ErrorCode.CODE_SEND_LIMIT.getCode(), "验证码发送过于频繁，请稍后再试");
        }
        
        // 检查发送频率限制 - 每天
        String dayLimitKey = "sms:limit:day:" + phone;
        String dayCount = redisTemplate.opsForValue().get(dayLimitKey);
        if (dayCount != null && Integer.parseInt(dayCount) >= sendLimitPerDay) {
            throw new BusinessException(ErrorCode.CODE_SEND_LIMIT.getCode(), "今日验证码发送次数已达上限");
        }
        
        // 生成6位验证码
        String code = String.format("%06d", (int)(Math.random() * 1000000));
        
        // 存储验证码到Redis,5分钟过期
        String codeKey = "sms:code:" + phone;
        redisTemplate.opsForValue().set(codeKey, code, 5, TimeUnit.MINUTES);
        
        // 更新发送次数 - 每分钟
        redisTemplate.opsForValue().increment(minuteLimitKey);
        redisTemplate.expire(minuteLimitKey, 1, TimeUnit.MINUTES);
        
        // 更新发送次数 - 每天
        redisTemplate.opsForValue().increment(dayLimitKey);
        redisTemplate.expire(dayLimitKey, 24, TimeUnit.HOURS);
        
        // 发送短信
        smsService.sendCode(phone, code);
    }
    
    /**
     * 登录/注册
     */
    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        // 验证验证码
        String codeKey = "sms:code:" + request.getPhone();
        String storedCode = redisTemplate.opsForValue().get(codeKey);
        if (storedCode == null || !storedCode.equals(request.getCode())) {
            throw new BusinessException(ErrorCode.CODE_INVALID.getCode(), ErrorCode.CODE_INVALID.getMessage());
        }
        
        // 验证成功后删除验证码
        redisTemplate.delete(codeKey);
        
        // 查找用户,如果不存在则创建
        User user = userMapper.selectByPhone(request.getPhone());
        boolean isNewUser = false;
        
        if (user == null) {
            // 自动注册
            user = new User();
            user.setPhone(request.getPhone());
            // 默认昵称: 用户+手机号后4位
            String phoneSuffix = request.getPhone().length() >= 4 
                ? request.getPhone().substring(7) 
                : "0000";
            user.setNickname(defaultNicknamePrefix + phoneSuffix);
            user.setIsEnabled(true);
            user.setIsDeleted(false);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);
            isNewUser = true;
            log.info("新用户注册: phone={}, userId={}", request.getPhone(), user.getId());
        }
        
        // 检查用户是否被禁用
        if (Boolean.FALSE.equals(user.getIsEnabled())) {
            throw new BusinessException(ErrorCode.USER_DISABLED.getCode(), ErrorCode.USER_DISABLED.getMessage());
        }
        
        // 更新登录信息
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userMapper.updateById(user);
        
        // 生成Token
        String token = jwtUtils.generateToken(user.getId(), user.getPhone());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getPhone());
        
        // 构建响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setRefreshToken(refreshToken);
        response.setUserId(user.getId());
        response.setNickname(user.getNickname());
        response.setAvatar(user.getAvatar());
        response.setExpiresIn(604800L);  // 7天
        
        log.info("用户登录: userId={}, phone={}, isNewUser={}", user.getId(), user.getPhone(), isNewUser);
        
        return response;
    }
    
    /**
     * 退出登录
     */
    public void logout(String token) {
        // 将Token加入黑名单
        try {
            Long userId = jwtUtils.getUserIdFromToken(token);
            redisTemplate.opsForValue().set("token:blacklist:" + token, "1", 7, TimeUnit.DAYS);
            log.info("用户退出登录: userId={}", userId);
        } catch (Exception e) {
            log.error("退出登录失败", e);
        }
    }
    
    /**
     * 刷新Token
     */
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID.getCode(), "刷新Token无效");
        }
        
        Claims claims = jwtUtils.getClaimsFromToken(refreshToken);
        if (!"refresh".equals(claims.get("type"))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID.getCode(), "Token类型错误");
        }
        
        Long userId = Long.valueOf(claims.get("userId").toString());
        String phone = claims.get("phone").toString();
        
        // 生成新的Token
        String newToken = jwtUtils.generateToken(userId, phone);
        String newRefreshToken = jwtUtils.generateRefreshToken(userId, phone);
        
        LoginResponse response = new LoginResponse();
        response.setToken(newToken);
        response.setRefreshToken(newRefreshToken);
        response.setUserId(userId);
        response.setExpiresIn(604800L);
        
        return response;
    }
}
