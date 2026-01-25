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
    private  List<Long> fileIds;

    public AgentContext(Long userId, Long conversationId) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.fileIds = new ArrayList<>();
    }

}
