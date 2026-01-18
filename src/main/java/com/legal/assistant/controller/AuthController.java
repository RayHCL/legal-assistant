package com.legal.assistant.controller;

import com.legal.assistant.annotation.NoAuth;
import com.legal.assistant.common.Result;
import com.legal.assistant.dto.request.LoginRequest;
import com.legal.assistant.dto.request.SendCodeRequest;
import com.legal.assistant.dto.response.LoginResponse;
import com.legal.assistant.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@Tag(name = "用户认证", description = "用户登录、注册、Token刷新等认证相关接口")
public class AuthController {
    
    @Autowired
    private AuthService authService;
    
    @NoAuth
    @PostMapping("/send-code")
    @Operation(summary = "发送验证码", description = "向指定手机号发送6位数字验证码，验证码有效期5分钟。开发环境下验证码会打印在控制台。")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendCode(request.getPhone());
        return Result.success();
    }
    
    @NoAuth
    @PostMapping("/login")
    @Operation(summary = "用户登录/注册", description = "使用手机号和验证码登录。如果账号不存在，系统会自动创建新账号（自动注册）。登录成功后返回JWT Token。")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        LoginResponse response = authService.login(request, clientIp);
        return Result.success(response);
    }
    
    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "退出登录，将当前Token加入黑名单，使其失效。需要Token认证。")
    public Result<Void> logout(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        authService.logout(token);
        return Result.success();
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "使用刷新Token获取新的访问Token和刷新Token。需要Token认证。")
    public Result<LoginResponse> refreshToken(@RequestHeader("Authorization") String bearerToken) {
        String token = bearerToken.substring(7);
        LoginResponse response = authService.refreshToken(token);
        return Result.success(response);
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
    
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
