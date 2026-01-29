package com.legal.assistant.controller;

import com.legal.assistant.common.Result;
import com.legal.assistant.dto.request.ConversationRequest;
import com.legal.assistant.dto.response.ConversationListResponse;
import com.legal.assistant.dto.response.ConversationResponse;
import com.legal.assistant.entity.Message;
import com.legal.assistant.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/conversation")
@Tag(name = "会话管理", description = "会话管理相关接口，包括创建、查询、重命名、置顶、删除会话等操作")
public class ConversationController {
    
    @Autowired
    private ConversationService conversationService;
    

    
    @GetMapping("/list")
    @Operation(summary = "获取会话列表", description = "获取当前用户的会话列表，分为置顶、今天、历史三个分类。支持游标分页。需要Token认证。")
    public Result<ConversationListResponse> getConversationList(
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "上一页最后一条记录的ID（用于分页）", example = "100")
            @RequestParam(required = false) String lastId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        ConversationListResponse response = conversationService.getConversationList(userId, size, lastId);
        return Result.success(response);
    }
    

    @PostMapping("/{conversationId}/rename")
    @Operation(summary = "重命名会话", description = "修改会话的标题。需要Token认证。")
    public Result<Void> renameConversation(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable Long conversationId,
            @Parameter(description = "新标题", required = true, example = "我的法律咨询")
            @RequestParam String newTitle,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        conversationService.renameConversation(userId, conversationId, newTitle);
        return Result.success();
    }
    
    @PostMapping("/{conversationId}/pin")
    @Operation(summary = "置顶/取消置顶会话", description = "将会话置顶或取消置顶。置顶的会话会在列表中优先显示。需要Token认证。")
    public Result<Void> pinConversation(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable Long conversationId,
            @Parameter(description = "是否置顶", required = true, example = "true")
            @RequestParam Boolean pinned,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        conversationService.pinConversation(userId, conversationId, pinned);
        return Result.success();
    }
    
    @GetMapping("/{conversationId}")
    @Operation(summary = "删除会话", description = "删除指定的会话（软删除），会话数据会保留30天。删除会话的同时会删除关联的所有消息。需要Token认证。")
    public Result<Void> deleteConversation(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable Long conversationId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        conversationService.deleteConversation(userId, conversationId);
        return Result.success();
    }
    
    @GetMapping("/{conversationId}/messages")
    @Operation(summary = "获取会话消息历史", description = "获取指定会话的所有消息历史记录，按创建时间升序排列。需要Token认证。")
    public Result<List<Message>> getMessages(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable Long conversationId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Message> messages = conversationService.getMessages(userId, conversationId);
        return Result.success(messages);
    }
}
