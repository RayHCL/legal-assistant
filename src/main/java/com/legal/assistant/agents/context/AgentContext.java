package com.legal.assistant.agents.context;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author hcl
 * @date 2026-01-23 09:29:57
 * @description
 */
@Data
public class AgentContext {
    private  Long userId;
    private  Long conversationId;
    private Long messageId;
    private  List<Long> fileIds;
    /**
     * 最近生成的报告ID，用于下一轮对话中生成PDF下载链接
     */
    private String lastReportId;

    /**
     * 是否启用深度思考（推理过程展示，如 DeepSeek R1 / Qwen 思考模式）
     */
    private Boolean deepThinking;

    public AgentContext(Long userId, Long conversationId) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.fileIds = new ArrayList<>();
    }

}
