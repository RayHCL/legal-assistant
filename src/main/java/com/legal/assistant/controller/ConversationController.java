package com.legal.assistant.controller;

import com.legal.assistant.common.Result;
import com.legal.assistant.dto.request.MessageFeedbackRequest;
import com.legal.assistant.dto.request.PinConversationRequest;
import com.legal.assistant.dto.request.RenameConversationRequest;
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
    @Operation(summary = "获取会话列表", description = "置顶和今天的会话全部返回、不分页；仅历史会话分页（page/size/total/totalPages 针对历史列表）。需要Token认证。")
    public Result<ConversationListResponse> getConversationList(
            @Parameter(description = "历史列表页码（从 1 开始）", example = "1")
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "历史列表每页数量", example = "20")
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        ConversationListResponse response = conversationService.getConversationList(userId, page, size);
        return Result.success(response);
    }
    

    @PostMapping("/{conversationId}/rename")
    @Operation(summary = "重命名会话", description = "修改会话的标题。参数在 body 中。需要Token认证。")
    public Result<Void> renameConversation(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable Long conversationId,
            @RequestBody @Valid RenameConversationRequest body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        conversationService.renameConversation(userId, conversationId, body.getNewTitle());
        return Result.success();
    }

    @PostMapping("/{conversationId}/pin")
    @Operation(summary = "置顶/取消置顶会话", description = "将会话置顶或取消置顶。参数在 body 中。需要Token认证。")
    public Result<Void> pinConversation(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable Long conversationId,
            @RequestBody @Valid PinConversationRequest body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        conversationService.pinConversation(userId, conversationId, body.getPinned());
        return Result.success();
    }
    
    @PostMapping("/delete/{conversationId}")
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
    @Operation(summary = "获取会话消息历史", description = "获取指定会话的所有消息历史记录，按创建时间升序排列。")
    public Result<List<Message>> getMessages(
            @Parameter(description = "会话ID", required = true, example = "1")
            @PathVariable Long conversationId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        List<Message> messages = conversationService.getMessages(userId, conversationId);
        return Result.success(messages);
    }

    @PostMapping("/message/{messageId}/feedback")
    @Operation(summary = "消息点赞/点踩/取消", description = "对指定消息进行点赞、点踩或取消反馈。需为该消息所属会话的拥有者。")
        public Result<Void> setMessageFeedback(
            @Parameter(description = "消息ID", required = true, example = "1")
            @PathVariable Long messageId,
            @RequestBody @Valid MessageFeedbackRequest body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        conversationService.setMessageFeedback(userId, messageId, body);
        return Result.success();
    }

}
