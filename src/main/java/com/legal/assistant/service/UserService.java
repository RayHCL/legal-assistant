package com.legal.assistant.service;

import com.legal.assistant.dto.request.UpdateUserRequest;
import com.legal.assistant.dto.response.UserInfoResponse;
import com.legal.assistant.entity.User;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    /**
     * 获取用户信息
     */
    public UserInfoResponse getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }
        
        UserInfoResponse response = new UserInfoResponse();
        response.setId(user.getId());
        response.setNickName(user.getNickname());
        response.setPhoneNumber(maskPhone(user.getPhone()));  // 脱敏
        response.setAvatar(user.getAvatar());
        response.setBio(user.getBio());
        response.setCreatedAt(user.getCreatedAt());
        response.setLastLoginAt(user.getLastLoginAt());
        
        return response;
    }
    
    /**
     * 更新用户信息
     */
    @Transactional
    public void updateUserInfo(Long userId, UpdateUserRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }
        
        if (request.getNickname() != null && !request.getNickname().trim().isEmpty()) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }
        
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        
        log.info("更新用户信息: userId={}", userId);
    }
    
    /**
     * 注销账号
     */
    @Transactional
    public void deactivateAccount(Long userId, String code) {
        // 二次验证: 需要再次验证验证码
        User user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }
        
        // 验证验证码
        String codeKey = "sms:code:" + user.getPhone();
        String storedCode = redisTemplate.opsForValue().get(codeKey);
        if (storedCode == null || !storedCode.equals(code)) {
            throw new BusinessException(ErrorCode.CODE_INVALID.getCode(), "验证码错误或已过期");
        }
        
        // 验证成功后删除验证码
        redisTemplate.delete(codeKey);
        
        // 软删除用户
        user.setIsDeleted(true);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        
        // 将用户的所有Token加入黑名单
        // (实际实现中需要维护用户Token列表,这里简化处理)
        log.info("用户注销账号: userId={}, phone={}", userId, user.getPhone());
    }
    
    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
