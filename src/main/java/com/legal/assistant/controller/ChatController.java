package com.legal.assistant.controller;

import com.legal.assistant.common.Result;
import com.legal.assistant.dto.request.ChatRequest;
import com.legal.assistant.dto.response.StreamChatResponse;
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

import java.time.Duration;

/**
 * 智能问答控制器
 * 使用Flux实现流式返回
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@Tag(name = "智能问答", description = "统一问答接口，支持多种AI模型和Agent类型，提供智能化的法律咨询服务。支持流式输出（SSE）。")
public class ChatController {
    
    @Autowired
    private ChatService chatService;
    
    /**
     * 智能问答（流式输出）
     * 使用Flux返回流式响应，支持SSE格式
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "智能问答（流式输出）", 
            description = """
                    向AI助手提问，支持多种Agent类型和模型类型。
                    
                    **Agent类型：**
                    - LEGAL_CONSULTATION：普通法律咨询
                    - RISK_ASSESSMENT：风险评估
                    - DISPUTE_FOCUS：争议焦点分析
                    - CASE_ANALYSIS：案件分析
                    
                    **模型类型：**
                    - DEEPSEEK_CHAT：DeepSeek对话模型
                    - DEEPSEEK_REASONER：DeepSeek推理模型
                    
                    **返回格式：** SSE流式输出
                    
                    **响应状态：**
                    - thinking：思考中
                    - streaming：流式输出中
                    - completed：完成
                    - error：错误
                    
                    需要Token认证。
                    """
    )
    public Flux<StreamChatResponse> ask(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpRequest) {
        
        Long userId = (Long) httpRequest.getAttribute("userId");
        
        log.info("[ChatController] 收到问答请求, userId={}, agentType={}, modelType={}", 
                userId, request.getAgentType(), request.getModelType());
        
        return chatService.processChatStream(userId, request)
                // 添加心跳，防止连接超时
                .mergeWith(Flux.interval(Duration.ofSeconds(15))
                        .map(i -> new StreamChatResponse(null, null, "", "heartbeat", null, false)))
                // 设置超时
                .timeout(Duration.ofMinutes(5))
                // 错误处理
                .onErrorResume(error -> {
                    log.error("[ChatController] 流式输出错误", error);
                    return Flux.just(new StreamChatResponse(
                            null, null, 
                            "处理超时或发生错误：" + error.getMessage(), 
                            "error", null, true
                    ));
                })
                // 日志
                .doOnSubscribe(subscription -> 
                        log.debug("[ChatController] 开始流式输出, userId={}", userId))
                .doOnComplete(() -> 
                        log.debug("[ChatController] 流式输出完成, userId={}", userId))
                .doOnCancel(() -> 
                        log.info("[ChatController] 流式输出被取消, userId={}", userId));
    }
    
    /**
     * 停止流式输出
     */
    @PostMapping("/stop")
    @Operation(
            summary = "停止流式输出", 
            description = "立即停止指定消息的流式输出。需要Token认证。"
    )
    public Result<Void> stopStream(
            @Parameter(description = "消息ID", required = true) 
            @RequestParam Long messageId,
            HttpServletRequest httpRequest) {
        
        Long userId = (Long) httpRequest.getAttribute("userId");
        log.info("[ChatController] 停止流式输出, userId={}, messageId={}", userId, messageId);
        

        
        return Result.success(null);
    }
    
    /**
     * 非流式问答接口（备用）
     * 适用于简单场景或测试
     */
    @PostMapping(value = "/ask/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "智能问答（非流式）", 
            description = "向AI助手提问，返回完整响应。适用于简单场景。需要Token认证。"
    )
    public Result<StreamChatResponse> askSync(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpRequest) {
        
        Long userId = (Long) httpRequest.getAttribute("userId");
        
        log.info("[ChatController] 收到同步问答请求, userId={}, agentType={}", 
                userId, request.getAgentType());
        
        // 收集所有流式响应
        StringBuilder contentBuilder = new StringBuilder();
        StreamChatResponse[] lastResponse = new StreamChatResponse[1];
        
        chatService.processChatStream(userId, request)
                .doOnNext(response -> {
                    if (response.getContent() != null && !"heartbeat".equals(response.getStatus())) {
                        contentBuilder.append(response.getContent());
                    }
                    lastResponse[0] = response;
                })
                .blockLast(Duration.ofMinutes(5));
        
        // 构建最终响应
        if (lastResponse[0] != null) {
            lastResponse[0].setContent(contentBuilder.toString());
            return Result.success(lastResponse[0]);
        }
        
        return Result.error(500, "处理失败");
    }
}
