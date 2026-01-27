package com.legal.assistant;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;

/**
 * @author hcl
 * @date 2026-01-27 09:50:39
 * @description
 */
public class TestAgent {

    public static void main(String[] args) {
        // 创建模型
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("sk-197a1ca0bca44229830a9a7ff2486347")
                .modelName("qwen-plus")
                .build();

        // 创建子智能体的 Provider（工厂）
        // 注意：必须使用 lambda 表达式，确保每次调用创建新实例
        Toolkit toolkit = new Toolkit();
        toolkit.registration()
                .subAgent(() -> ReActAgent.builder()
                        .name("Trans")
                        .sysPrompt("你是一个16进制转化器，你能将用户的提问转化为16进制")
                        .model(model)
                        .build())
                .apply();

        // 创建主智能体，配置工具
        ReActAgent mainAgent = ReActAgent.builder()
                .name("Coordinator")
                .sysPrompt("你是一个协调员。当遇到问题时，先使用调用Trans工具去处理。然后返回处理结果")
                .model(model)
                .maxIters(3)
                .toolkit(toolkit)
                .build();

        Msg inputMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent("生成4条刑法，然后转化成16进制")
                .build();

        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.ALL)
                .incremental(true)
                .includeReasoningResult(true)
                .includeActingChunk(true)
                .build();

        // 执行流式推理并打印结果
        mainAgent.stream(inputMsg, streamOptions)
                .doOnNext(event -> {
                    Msg msg = event.getMessage();
                    if (msg != null) {
                        String content = msg.getTextContent();
                        if (content != null && !content.isEmpty()) {
                            System.out.println("=== " + event.getType() + " ===");
                            System.out.println(content);

                        }
                    }
                })
                .doOnComplete(() -> {
                    System.out.println("\n\n=== 流式输出完成 ===");
                })
                .doOnError(error -> {
                    System.err.println("发生错误: " + error.getMessage());
                    error.printStackTrace();
                })
                .blockLast(); // 阻塞等待流完成
    }
}
