package com.legal.assistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmsService {
    
    @Value("${sms.access-key-id:}")
    private String accessKeyId;
    
    @Value("${sms.access-key-secret:}")
    private String accessKeySecret;
    
    @Value("${sms.sign-name:法律咨询助手}")
    private String signName;
    
    @Value("${sms.template-code:}")
    private String templateCode;
    
    /**
     * 发送验证码短信
     * 注意: 这里只是示例,实际需要根据使用的短信服务商实现
     * 例如: 阿里云、腾讯云、云片等
     * 开发环境可以直接打印到日志,生产环境需要实际调用短信服务
     */
    public void sendCode(String phone, String code) {
        // TODO: 实现短信发送逻辑
        // 示例: 使用阿里云短信服务
        // 1. 构建短信内容
        // 2. 调用短信服务API
        // 3. 处理发送结果
        
        // 开发环境可以打印到日志,生产环境需要实际调用短信服务
        log.info("【开发环境】发送验证码到 {}: {}", phone, code);
        System.out.println("========================================");
        System.out.println("验证码: " + code);
        System.out.println("手机号: " + phone);
        System.out.println("========================================");
    }
}
