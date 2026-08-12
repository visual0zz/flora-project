package com.flora.root.ai.orchestration;

import com.flora.root.ai.api.*;
import com.root.ai.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolRegistry} 与 {@link Session} 测试：工具注册/执行/错误回执，turn 上下文回写。
 */
class SessionTest {

    private static final ToolRegistry WEATHER_TOOLS = new ToolRegistry()
            .register(ToolSpec.of("get_weather", "查询天气", Map.of("type", "object")),
                    call -> Message.toolResult(call.id(), "sunny in " + call.arguments().get("city")));

    /** 记录请求的假客户端。 */
    private static ChatClient recordingClient(ChatResponse toReturn) {
        return new ChatClient() {
            @Override
            public java.util.Set<Capability> capabilities() {
                return java.util.Set.of();
            }

            @Override
            public ChatResponse chat(ChatRequest request) {
                return toReturn;
            }
        };
    }

    private static ChatResponse textResponse(String text) {
        return new ChatResponse(text, null, List.of(), TokenUsage.ZERO, "stop", null);
    }

    private static ChatResponse toolCallResponse() {
        return new ChatResponse("let me check", null,
                List.of(ToolCall.of("call_1", "get_weather", Map.of("city", "beijing"))),
                TokenUsage.ZERO, "tool_calls", null);
    }

    @Test
    void turnAppendsUserAndAssistantToContext() {
        Context ctx = new Context();
        ChatProjector p = ChatProjector.of(List.of(), new TokenBudget(1000), WEATHER_TOOLS, null);
        Session s = new Session(ctx, p, recordingClient(textResponse("hi back")), WEATHER_TOOLS);
        ChatResponse resp = s.turn("hello");
        assertEquals("hi back", resp.text());
        assertEquals(2, ctx.size());
        assertEquals(Message.Role.USER, ctx.messages().get(0).role());
        assertEquals(Message.Role.ASSISTANT, ctx.messages().get(1).role());
    }

    @Test
    void executeToolsRunsAndReturnsReceipts() {
        Context ctx = new Context();
        ChatProjector p = ChatProjector.of(List.of(), new TokenBudget(1000), WEATHER_TOOLS, null);
        Session s = new Session(ctx, p, recordingClient(toolCallResponse()), WEATHER_TOOLS);
        ChatResponse resp = s.turn("weather in beijing?");
        // turn 内部先记录 assistant（含 toolCalls）
        assertTrue(resp.hasToolCalls());
        assertEquals(2, ctx.size());
        // 执行工具 → 回执写入上下文
        assertTrue(s.executeTools(resp));
        assertEquals(3, ctx.size());
        Message receipt = ctx.messages().get(2);
        assertEquals(Message.Role.TOOL, receipt.role());
        String text = ((ContentBlock.Text) receipt.content().get(0)).text();
        assertTrue(text.contains("sunny in beijing"), "回执内容应为执行结果: " + text);
    }

    @Test
    void executeToolsReturnsFalseWhenNoToolCalls() {
        Context ctx = new Context();
        ChatProjector p = ChatProjector.of(List.of(), new TokenBudget(1000), WEATHER_TOOLS, null);
        Session s = new Session(ctx, p, recordingClient(textResponse("plain")), WEATHER_TOOLS);
        ChatResponse resp = s.turn("hi");
        assertFalse(s.executeTools(resp));
        assertEquals(2, ctx.size(), "无工具调用不应追加回执");
    }

    @Test
    void executeUnknownToolYieldsErrorReceipt() {
        ToolCall unknown = ToolCall.of("call_x", "no_such_tool", Map.of());
        ToolRegistry reg = new ToolRegistry(); // 空注册表
        Message receipt = reg.execute(unknown);
        assertEquals(Message.Role.TOOL, receipt.role());
        assertTrue(receipt.error(), "未注册工具应标记错误");
    }

    @Test
    void executeThrowingExecutorYieldsErrorReceipt() {
        ToolRegistry reg = new ToolRegistry()
                .register(ToolSpec.of("boom", "always fails", Map.of()),
                        call -> {
                            throw new IllegalStateException("kaboom");
                        });
        Message receipt = reg.execute(ToolCall.of("call_1", "boom", Map.of()));
        assertTrue(receipt.error(), "执行器抛异常应转错误回执");
        assertTrue(receipt.content().get(0).toString().contains("kaboom"));
    }

    @Test
    void multimodalTurnInput() {
        Context ctx = new Context();
        ChatProjector p = ChatProjector.of(List.of(), new TokenBudget(1000), WEATHER_TOOLS, null);
        Session s = new Session(ctx, p, recordingClient(textResponse("i see an image")), WEATHER_TOOLS);
        s.turn(List.of(new ContentBlock.Text("what"),
                new ContentBlock.Image("data:image/png;base64,zz", "image/png")));
        assertEquals(2, ctx.size());
        assertEquals(2, ctx.messages().get(0).content().size());
    }

    @Test
    void turnWithAutoCompactKeepsRecentAndSummarizes() {
        Context ctx = new Context();
        for (int i = 0; i < 30; i++) {
            ctx.appendUser("msg-" + i + " with padding text here to occupy tokens");
        }
        Compactor summarizer = Compactor.of(history -> {
            StringBuilder sb = new StringBuilder();
            for (Message m : history) {
                sb.append(m.text()).append(' ');
            }
            return sb.toString();
        });
        ChatProjector p = ChatProjector.of(List.of(), new TokenBudget(80), WEATHER_TOOLS, null);
        // retention=4：30旧 + 新user = 31 条 → 压缩 27 条 → 1摘要 + 4保留(含新user) + assistant = 6
        Session s = new Session(ctx, p, recordingClient(textResponse("compacted reply")),
                WEATHER_TOOLS, summarizer, 4);
        s.turn("continue");
        assertEquals(6, ctx.size());
        assertEquals(Message.Role.ASSISTANT, ctx.messages().get(0).role(), "首条应为摘要");
        assertEquals(Message.Role.USER, ctx.messages().get(ctx.size() - 2).role(), "最后应为新增用户输入");
        assertEquals(Message.Role.ASSISTANT, ctx.messages().get(ctx.size() - 1).role());
    }
}
