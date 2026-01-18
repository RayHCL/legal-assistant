package com.legal.assistant.controller;

import com.legal.assistant.common.Result;
import com.legal.assistant.dto.request.UpdateUserRequest;
import com.legal.assistant.dto.response.UserInfoResponse;
import com.legal.assistant.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/user")
@Tag(name = "用户信息", description = "用户信息管理相关接口，包括查看、编辑个人信息和账号注销")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping("/info")
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的详细信息，包括昵称、手机号（脱敏）、头像、个人简介等。需要Token认证。")
    public Result<UserInfoResponse> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        UserInfoResponse response = userService.getUserInfo(userId);
        return Result.success(response);
    }
    
    @PutMapping("/info")
    @Operation(summary = "更新用户信息", description = "更新当前登录用户的个人信息，包括昵称、头像、个人简介。手机号不支持修改。需要Token认证。")
    public Result<Void> updateUserInfo(@Valid @RequestBody UpdateUserRequest request, 
                                       HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        userService.updateUserInfo(userId, request);
        return Result.success();
    }
    
    @PostMapping("/deactivate")
    @Operation(summary = "注销账号", description = "注销当前登录用户的账号。需要输入验证码进行二次确认。注销后账号将被软删除，数据保留30天。需要Token认证。")
    public Result<Void> deactivateAccount(
            @Parameter(description = "验证码（6位数字）", required = true, example = "123456")
            @RequestParam String code, 
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userService.deactivateAccount(userId, code);
        return Result.success();
    }
}
