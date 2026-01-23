package com.legal.assistant.agents.context;

import lombok.Data;

import java.util.List;

/**
 * @author hcl
 * @date 2026-01-23 09:29:57
 * @description
 */
@Data
public class AgentContext {
    private final Long userId;
    private final Long conversationId;
    private final List<Long> fileIds;
}
