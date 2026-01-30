package com.legal.assistant;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agui.event.AguiEvent;
import io.agentscope.core.message.*;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试 Agent 流式输出，可看到思考过程（thinking）
 */
public class TestAgent {

    public static void main(String[] args) {
        // 开启深度思考，便于看到思考过程
        boolean enableThinking = true;
        GenerateOptions options = enableThinking
                ? GenerateOptions.builder().temperature(0.7).thinkingBudget(5000).build()
                : GenerateOptions.builder().temperature(0.7).build();
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey("sk-197a1ca0bca44229830a9a7ff2486347")
                .defaultOptions(options)
                .modelName("qwen-plus")
                .stream(true)
                .enableThinking(true)
                .build();
        ReActAgent mainAgent = ReActAgent.builder()
                .name("Coordinator")
                .sysPrompt("你是一个法律方面的专家，你能回答提出的法律方面问题")
                .model(model)
                .maxIters(3)
                .build();

        Msg inputMsg = Msg.builder()
                .role(MsgRole.USER)
                .textContent("生成10个关于食品安全的案件信息，我要作为报告进行选择")
                .build();



        // 流式选项：订阅所有事件，包含推理/思考结果
        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.ALL)
                .incremental(true)
                .includeReasoningResult(false)
                .includeReasoningChunk(true)
                .includeActingChunk(true)
                .build();

        System.out.println("========== 流式输出（含思考过程） ==========\n");

        Flux<Event> stream = mainAgent.stream(inputMsg, streamOptions);

        stream
                .doOnNext(event -> printEvent(event))
                .doOnComplete(() -> System.out.println("\n========== 流式输出结束 =========="))
                .doOnError(e -> System.err.println("流式错误: " + e.getMessage()))
                .blockLast();
    }

    /**
     * 根据事件类型和内容块打印：思考过程用 [思考] 前缀，普通回复用 [回复]
     */
    private static void printEvent(Event event) {
        Msg msg = event.getMessage();
        if (msg == null || msg.getContent() == null) {
            return;
        }

        if (event.getType()== EventType.REASONING){
            List<ContentBlock> contents = msg.getContent();
            for (ContentBlock block : contents) {
                if (block instanceof ThinkingBlock){
                    ThinkingBlock thinkingBlock = (ThinkingBlock) block;
                    System.out.println("[思考] " + thinkingBlock.getThinking());
                }

            }
        }


    }

    private static boolean isThinkingBlock(ContentBlock block) {
        return block != null && "ThinkingBlock".equals(block.getClass().getSimpleName());
    }
}
