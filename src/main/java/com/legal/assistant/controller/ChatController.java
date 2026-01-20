package com.legal.assistant.controller;

import com.legal.assistant.common.Result;
import com.legal.assistant.dto.request.ChatCompletionRequest;
import com.legal.assistant.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

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
    public Flux<String> chatCompletion(
            @Valid @RequestBody ChatCompletionRequest request,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("收到对话请求: userId={}, agentType={}, question={}",
                userId, request.getAgentType(), request.getQuestion());

        return chatService.createChatStream(userId, request);
    }

    @PostMapping("/stop")
    @Operation(summary = "停止对话", description = "停止正在进行的流式对话")
    public Result<Void> stopChat(
            @RequestBody Map<String, Long> request,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        Long conversationId = request.get("conversationId");

        log.info("收到停止请求: userId={}, conversationId={}", userId, conversationId);
        chatService.stopChat(userId, conversationId);

        return Result.success();
    }
}
