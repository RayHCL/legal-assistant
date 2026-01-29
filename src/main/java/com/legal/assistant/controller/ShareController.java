package com.legal.assistant.controller;

import com.legal.assistant.annotation.NoAuth;
import com.legal.assistant.common.Result;
import com.legal.assistant.dto.request.CreateShareRequest;
import com.legal.assistant.dto.response.ShareDetailResponse;
import com.legal.assistant.dto.response.ShareResponse;
import com.legal.assistant.service.ShareService;
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
@RequestMapping("/api/message/share")
@Tag(name = "消息分享", description = "消息分享相关接口")
public class ShareController {
    
    @Autowired
    private ShareService shareService;
    
    @PostMapping("/create")
    @Operation(summary = "创建分享", description = "选择要分享的消息ID列表，生成分享ID。")
    public Result<ShareResponse> createShare(
            @Valid @RequestBody CreateShareRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        ShareResponse response = shareService.createShare(userId, request.getMessageIds());
        return Result.success(response);
    }
    
    @NoAuth
    @GetMapping("/get/{shareId}")
    @Operation(summary = "获取分享详情", description = "通过分享ID查询之前存储的消息集合。")
    public Result<ShareDetailResponse> getShareDetail(
            @Parameter(description = "分享ID", required = true, example = "share_abc123def456")
            @PathVariable String shareId) {
        ShareDetailResponse response = shareService.getShareDetail(shareId);
        return Result.success(response);
    }
}
