package com.legal.assistant.controller;

import com.legal.assistant.annotation.NoAuth;
import com.legal.assistant.common.Result;
import com.legal.assistant.dto.request.LoginRequest;
import com.legal.assistant.dto.request.RefreshTokenRequest;
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
    @Operation(summary = "发送验证码", description = "向指定手机号发送6位数字验证码，验证码有效期5分钟。")
    public Result<String> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendCode(request.getPhoneNumber());
        return Result.success("验证码发送成功");
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
    @Operation(summary = "用户登出", description = "用户登出，使当前token失效。")
    public Result<String> logout(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        authService.logout(token);
        return Result.success("登出成功");
    }
    
    @NoAuth
    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "使用刷新令牌获取新的访问令牌。")
    public Result<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request.getRefreshToken());
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
