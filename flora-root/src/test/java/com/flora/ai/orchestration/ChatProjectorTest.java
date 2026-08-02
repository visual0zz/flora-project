package com.flora.ai.orchestration;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ContentBlock;
import com.flora.ai.api.InferenceConfig;
import com.flora.ai.api.Message;
import com.flora.ai.api.ToolCall;
import com.flora.ai.api.ToolSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ChatProjector} 投影测试：注入器链、预算裁剪、工具声明、多模态、无副作用。
 */
class ChatProjectorTest {

    private static final ToolRegistry NO_TOOLS = new ToolRegistry();

    private static ChatProjector projector(List<Injector> injectors, int maxTokens) {
        return ChatProjector.of(injectors, new TokenBudget(maxTokens), NO_TOOLS, null);
    }

    @Test
    void assemblesSystemInjectedAndHistory() {
        Context ctx = new Context().appendUser("hello");
        ChatProjector p = projector(List.of(
                Injector.of(() -> {
                }) /* noop */), 1000);
        ChatRequest req = p.assemble(ctx);
        assertEquals(1, req.messages().size());
        assertEquals(Message.Role.USER, req.messages().get(0).role());
    }

    @Test
    void systemInjectorSetsRequestSystem() {
        Context ctx = new Context().appendUser("hi");
        ChatProjector p = projector(List.of(
                b -> {
                    b.system("you are claude");
                    return CompletableFuture.completedFuture(null);
                }), 1000);
        ChatRequest req = p.assemble(ctx);
        assertEquals("you are claude", req.system());
    }

    @Test
    void injectedMessagesGoBeforeHistory() {
        Context ctx = new Context().appendUser("what is flora?");
        ChatProjector p = projector(List.of(
                b -> {
                    b.inject(Message.of(Message.Role.USER, "[RAG] flora is a java toolkit"));
                    return CompletableFuture.completedFuture(null);
                }), 1000);
        ChatRequest req = p.assemble(ctx);
        assertEquals(2, req.messages().size());
        assertEquals("[RAG] flora is a java toolkit",
                ((ContentBlock.Text) req.messages().get(0).content().get(0)).text());
        assertEquals("what is flora?", ((ContentBlock.Text) req.messages().get(1).content().get(0)).text());
    }

    @Test
    void budgetTrimsOldestHistory() {
        // 每条消息约 2-4 token，预算 10 只够保留最近几条
        Context ctx = new Context();
        for (int i = 0; i < 50; i++) {
            ctx.appendUser("message number " + i + " with some padding text here");
        }
        ChatProjector p = projector(List.of(), 40);
        ChatRequest req = p.assemble(ctx);
        assertFalse(req.messages().isEmpty());
        assertTrue(req.messages().size() < 50, "预算应裁剪历史");
        // 裁剪应从最旧开始丢，保留最近的消息
        String last = ((ContentBlock.Text) req.messages().get(req.messages().size() - 1).content().get(0)).text();
        assertTrue(last.contains("49"), "应保留最近的消息，实际最后一条: " + last);
    }

    @Test
    void toolsDeclarationsFlowIntoRequest() {
        ToolRegistry tools = new ToolRegistry()
                .register(ToolSpec.of("get_weather", "查询天气", Map.of("type", "object")),
                        call -> Message.toolResult(call.id(), "sunny"));
        ChatProjector p = ChatProjector.of(List.of(), new TokenBudget(1000), tools, null);
        ChatRequest req = p.assemble(new Context().appendUser("weather?"));
        assertEquals(1, req.tools().size());
        assertEquals("get_weather", req.tools().get(0).name());
    }

    @Test
    void assembleDoesNotMutateContext() {
        Context ctx = new Context().appendUser("hi");
        int before = ctx.size();
        ChatProjector p = projector(List.of(b -> {
            b.system("sys");
            b.inject(Message.of(Message.Role.USER, "extra"));
            return CompletableFuture.completedFuture(null);
        }), 1000);
        p.assemble(ctx);
        assertEquals(before, ctx.size(), "投影不应修改 Context");
        assertEquals(1, ctx.messages().size());
    }

    @Test
    void asyncAssembleParallelInjectors() {
        Context ctx = new Context().appendUser("hi");
        ChatProjector p = projector(List.<Injector>of(
                b -> {
                    b.system("A");
                    return CompletableFuture.<Void>completedFuture(null);
                },
                b -> {
                    b.system("B");
                    return CompletableFuture.<Void>completedFuture(null);
                }), 1000);
        ChatRequest req = p.assembleAsync(ctx).join();
        assertEquals("A\n\nB", req.system());
    }

    @Test
    void multimodalUserInputMapsToBlocks() {
        Context ctx = new Context().appendUser(List.of(
                new ContentBlock.Text("what is this?"),
                new ContentBlock.Image("data:image/png;base64,xyz", "image/png")));
        // 预算须容纳图片块（IMAGE_TOKENS=1000），否则整条被裁掉
        ChatProjector p = projector(List.of(), 2000);
        ChatRequest req = p.assemble(ctx);
        assertEquals(1, req.messages().size());
        List<ContentBlock> blocks = req.messages().get(0).content();
        assertEquals(2, blocks.size());
        assertInstanceOf(ContentBlock.Image.class, blocks.get(1));
    }

    @Test
    void inferenceConfigPropagates() {
        Context ctx = new Context().appendUser("hi");
        ChatProjector p = ChatProjector.of(List.of(), new TokenBudget(1000), NO_TOOLS,
                InferenceConfig.of(128));
        ChatRequest req = p.assemble(ctx);
        assertNotNull(req.config());
        assertEquals(128, req.config().maxTokens());
    }
}
