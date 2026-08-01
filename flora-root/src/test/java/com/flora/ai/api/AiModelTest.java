package com.flora.ai.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 数据模型测试：请求构建与各配置。
 */
class AiModelTest {

    private static final ModelSpec SPEC = ModelSpec.of("gpt-5", "openai", ModelSpec.Size.LARGE);

    @Test
    void modelSpec() {
        assertEquals("gpt-5", SPEC.id());
        assertEquals("openai", SPEC.provider());
        assertEquals(ModelSpec.Size.LARGE, SPEC.size());
    }

    @Test
    void chatRequestBuilds() {
        ChatRequest req = ChatRequest.builder()
                .model(SPEC)
                .message(Message.of(Message.Role.USER, "hello"))
                .thinking(ThinkingConfig.of(ThinkingConfig.Mode.ON, ThinkingConfig.Effort.HIGH))
                .sampling(SamplingConfig.of(0.7, 512))
                .build();
        assertEquals(1, req.messages().size());
        assertEquals(ThinkingConfig.Mode.ON, req.thinking().mode());
        assertEquals(ThinkingConfig.Effort.HIGH, req.thinking().effort());
        assertEquals(0.7, req.sampling().temperature());
        assertEquals(512, req.sampling().maxTokens());
    }

    @Test
    void chatRequestRequiresModel() {
        assertThrows(IllegalStateException.class,
                () -> ChatRequest.builder().message(Message.of(Message.Role.USER, "x")).build());
    }

    @Test
    void messageRolesAndBlocks() {
        Message m = new Message(Message.Role.USER, List.of(
                new ContentBlock.Text("look at"),
                new ContentBlock.Image("data:image/png;base64,xxx", "image/png")));
        assertEquals(2, m.content().size());
        assertInstanceOf(ContentBlock.Image.class, m.content().get(1));
    }

    @Test
    void thinkingConfig() {
        assertTrue(ThinkingConfig.off().enabled() == false);
        assertTrue(ThinkingConfig.of(ThinkingConfig.Mode.ON, ThinkingConfig.Effort.MAX).enabled());
        assertEquals(ThinkingConfig.Mode.AUTO, ThinkingConfig.auto().mode());
    }

    @Test
    void samplingWithAnnealing() {
        SamplingConfig s = SamplingConfig.withAnnealing(1.0, 0.2, 100);
        assertEquals(1.0, s.annealing().startTemp());
        assertEquals(0.2, s.annealing().endTemp());
        assertEquals(100, s.maxTokens());
    }

    @Test
    void responseThinkingFlag() {
        assertTrue(new ChatResponse("a", "thought", null, "stop", null).isThinking());
        assertFalse(new ChatResponse("a", null, null, "stop", null).isThinking());
    }
}
