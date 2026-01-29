package com.legal.assistant.controller;

import com.legal.assistant.common.Result;
import com.legal.assistant.dto.request.ChangePhoneRequest;
import com.legal.assistant.dto.request.UpdateUserRequest;
import com.legal.assistant.dto.response.UserInfoResponse;
import com.legal.assistant.exception.BusinessException;
import com.legal.assistant.exception.ErrorCode;
import com.legal.assistant.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/user")
@Tag(name = "用户信息", description = "用户信息管理相关接口，包括查看、编辑个人信息和账号注销")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping("/profile")
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的详细信息。")
    public Result<UserInfoResponse> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        UserInfoResponse response = userService.getUserInfo(userId);
        return Result.success(response);
    }
    
    @PostMapping("/profile")
    @Operation(summary = "更新用户信息", description = "更新用户的昵称、个人简介、头像URL等信息。")
    public Result<String> updateUserInfo(@Valid @RequestBody UpdateUserRequest request, 
                                       HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        userService.updateUserInfo(userId, request);
        return Result.success("更新成功");
    }

    @PostMapping("/avatar")
    @Operation(summary = "上传头像", description = "上传用户头像图片，支持jpg、png等格式，最大5MB")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file,HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            log.warn("上传头像失败：未获取到用户信息");
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }
        log.info("用户 {} 上传头像请求", userId);
         String avatarUrl = userService.uploadAvatar(userId, file);
        return Result.success(avatarUrl);
    }

    @PostMapping("/change-phone")
    @Operation(summary = "修改手机号", description = "修改用户绑定的手机号，需要先向新手机号发送验证码。修改成功后当前 token 失效，需用新手机号重新登录")
    public Result<String> changePhoneNumber(@Valid @RequestBody ChangePhoneRequest request, HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            log.warn("修改手机号失败：未获取到用户信息");
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }
        String token = getTokenFromRequest(httpRequest);
        log.info("用户 {} 修改手机号请求，新手机号: {}", userId, request.getNewPhoneNumber());
        userService.changePhoneNumber(userId, request, token);
        return Result.success("手机号修改成功，请使用新手机号重新登录");
    }

    @PostMapping("/deactivate")
    @Operation(summary = "注销账户", description = "注销当前用户账户，会逻辑删除所有会话、消息、上传的文件、分享、报告等数据，并使当前token失效")
    public Result<String> deactivateAccount(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        if (userId == null) {
            log.warn("注销账户失败：未获取到用户信息");
            throw new BusinessException(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }
        
        // 从请求头获取token
        String token = getTokenFromRequest(httpRequest);
        
        log.info("用户 {} 请求注销账户", userId);
        userService.deactivateAccount(userId, token);
        return Result.success("账户注销成功");
    }

    /**
     * 从请求头获取Token
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
