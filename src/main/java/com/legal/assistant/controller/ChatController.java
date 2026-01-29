package com.legal.assistant.controller;

import com.legal.assistant.annotation.NoAuth;
import com.legal.assistant.common.Result;
import com.legal.assistant.dto.request.ChatCompletionRequest;
import com.legal.assistant.dto.response.StreamChatResponse;
import com.legal.assistant.dto.response.SuggestedQuestionsResponse;
import com.legal.assistant.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * 智能问答控制器
 * 使用AgentScope实现流式对话
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@Tag(name = "智能问答", description = "基于AgentScope的智能对话接口，支持流式输出")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping(value = "/completion", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话", description = "创建流式对话，使用SSE方式返回响应")
    @NoAuth
    public Flux<StreamChatResponse> chatCompletion(
            @Valid @RequestBody ChatCompletionRequest request,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("收到对话请求: userId={}, agentType={}, question={}",
                userId, request.getAgentType(), request.getQuestion());
        return chatService.createChatStream(userId, request);
    }

    @GetMapping("/stop")
    @Operation(summary = "停止对话", description = "停止正在进行的流式对话，会保存已生成的内容并标记消息状态为已停止")
    @NoAuth
    public Result<Void> stopChat(
            @Parameter(description = "会话ID", required = true)
            @RequestParam Long conversationId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("收到停止请求: userId={}, conversationId={}", userId, conversationId);
        chatService.stopChat(userId, conversationId);
        return Result.success();
    }

    @GetMapping("/suggested-questions")
    @Operation(summary = "获取建议问题列表", description = "根据当前会话的对话历史，生成3-5个建议性的下一轮问题供用户选择")
    @NoAuth
    public Result<SuggestedQuestionsResponse> getSuggestedQuestions(
            @Parameter(description = "会话ID", required = true, example = "1")
            @RequestParam Long conversationId,
            HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("获取建议问题请求: userId={}, conversationId={}", userId, conversationId);
        SuggestedQuestionsResponse response = chatService.getSuggestedQuestions(userId, conversationId);
        return Result.success(response);
    }

}
