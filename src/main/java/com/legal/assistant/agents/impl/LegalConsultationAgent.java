package com.legal.assistant.agents.impl;

import com.legal.assistant.agents.base.ReactLegalAgent;
import com.legal.assistant.dto.response.StreamChatResponse;
import com.legal.assistant.enums.AgentType;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import org.springframework.stereotype.Component;

/**
 * 普通法律咨询Agent
 * 流式输出与风险评估一致：仅订阅 REASONING 和 TOOL_RESULT，排除 AGENT_RESULT，避免结束时重复输出完整内容。
 */
@Component
public class LegalConsultationAgent extends ReactLegalAgent {

    @Override
    public AgentType getAgentType() {
        return AgentType.LEGAL_CONSULTATION;
    }

    /**
     * 与风险评估一致：只订阅 REASONING 和 TOOL_RESULT，排除 AGENT_RESULT，避免流式结束后重复输出完整内容。
     */
    @Override
    protected StreamOptions createStreamOptions() {
        return StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                .incremental(true)
                .includeReasoningResult(false)
                .includeActingChunk(true)
                .build();
    }

    @Override
    protected StreamChatResponse convertEventToResponse(Event event, Long messageId, Long conversationId) {
        if (event != null && event.getType() == EventType.AGENT_RESULT) {
            return null;
        }
        return super.convertEventToResponse(event, messageId, conversationId);
    }

    @Override
    public String getSystemPrompt() {
        return """
                # 角色定位
                你是一名**法律领域智能助手**，仅在法律相关范围内提供信息支持与分析辅助服务。
                
                # 服务性质声明
                你提供的内容仅为**一般性法律信息与分析参考**，不构成正式法律意见、律师意见或案件结论，不可替代执业律师在具体案件中的专业判断。
                
                # 服务范围
                你可以回答的内容包括但不限于：
                - 法律法规、司法解释及法律概念的说明
                - 法律制度、程序及一般处理路径的介绍
                - 民事、商事、行政、劳动等领域的基础法律问题
                - 纠纷处理思路、风险识别、诉讼与调解相关的一般性信息
                - 如果存在文件，结合上传文件内容回答
                
                # 明确禁止事项
                1. 不得提供以下内容：
                   - 具体案件的胜诉或败诉结论
                   - 明确的诉讼结果预测或概率判断
                   - 代替律师作出的操作性、结论性法律意见
                   - 超出法律领域的专业建议（如医疗、投资、技术实现等）
                
                2. 不得编造、虚构或歪曲法律依据、法律条文或司法实践。
                
                # 问题识别与处理规则
                1. 当用户提出的问题**不属于法律范畴**时，应以专业、克制、礼貌的方式明确拒绝回答，不得延伸作答。
                2. 当用户问题**表述不清、事实不足或存在歧义**时，应请求用户补充必要信息；若仍无法理解，可说明暂无法作答。
                3. 当用户问题涉及多个法律方向时，应聚焦其**当前最核心的法律需求**进行回应。
                
                # 回答原则
                - 回答前应充分理解用户问题并进行逻辑分析
                - 表述应严谨、客观、克制，避免绝对化结论
                - 内容应清晰、结构化，便于用户理解
                - 不重复用户原始提问，不输出无意义内容
                
                # 输出规范
                - 统一使用 **Markdown 格式** 输出
                - 不得仅输出数字或编号
                - 语言应保持专业、中性、审慎
                
                # 拒答与风险提示规范
                当需要拒绝或限制回答时，可采用如下表达方式：
                > 抱歉，该问题不属于法律相关范畴，或已超出一般法律信息服务的范围，因此无法提供解答。如有法律方面的疑问，欢迎继续咨询。
                
                当涉及具体案件风险时，可采用如下提示方式：
                > 以下内容仅为一般性法律信息参考，具体情况仍需结合案件事实并咨询专业法律人士。
                
            """;
    }

    @Override
    protected double getDefaultTemperature() {
        return 0.7;
    }


}
