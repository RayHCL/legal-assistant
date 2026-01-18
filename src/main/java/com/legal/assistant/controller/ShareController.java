package com.legal.assistant.controller;

import com.legal.assistant.annotation.NoAuth;
import com.legal.assistant.common.Result;
import com.legal.assistant.service.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/share")
@Tag(name = "分享功能", description = "会话分享相关接口，包括创建分享链接和访问分享内容")
public class ShareController {
    
    @Autowired
    private ShareService shareService;
    
    @PostMapping("/{conversationId}")
    @Operation(summary = "创建分享", description = "为指定会话创建分享链接，可以设置过期时间和访问密码。需要Token认证。")
    public Result<Map<String, Object>> createShare(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable Long conversationId,
            @Parameter(description = "过期天数（可选，默认7天）", example = "7")
            @RequestParam(required = false) Integer expirationDays,
            @Parameter(description = "访问密码（可选）", example = "123456")
            @RequestParam(required = false) String password,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> result = shareService.createShare(userId, conversationId, expirationDays, password);
        return Result.success(result);
    }
    
    @NoAuth
    @GetMapping("/{shareId}")
    @Operation(summary = "访问分享", description = "通过分享ID访问分享的会话内容。如果设置了密码，需要提供密码。此接口无需Token认证。")
    public Result<Map<String, Object>> accessShare(
            @Parameter(description = "分享ID", required = true, example = "abc123...")
            @PathVariable String shareId,
            @Parameter(description = "访问密码（如果设置了密码）", example = "123456")
            @RequestParam(required = false) String password) {
        Map<String, Object> result = shareService.accessShare(shareId, password);
        return Result.success(result);
    }
}
