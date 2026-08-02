package com.flora.ai.api;

import com.flora.ai.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 数据模型测试：Endpoint 解析、请求构建与各配置。
 */
class AiModelTest {

    @Test
    void registeredModelFromJson() {
        String json = """
                {"id":"gpt","apiKind":"OPENAI_OFFICIAL","modelId":"gpt-5",
                 "baseUrl":"https://api.openai.com","apiKey":"sk","default":true,
                 "tags":["THINKING","MULTIMODAL"],"spec":{"contextWindow":128000},
                 "customFlag":"abc"}
                """;
        Endpoint m = Endpoint.fromJson(json);
        assertEquals("gpt", m.id());
        assertEquals(ApiKind.OPENAI_OFFICIAL, m.apiKind());
        assertEquals("gpt-5", m.modelId());
        assertTrue(m.isDefault());
        assertTrue(m.tags().containsAll(Set.of(Tag.THINKING, Tag.MULTIMODAL)));
        assertEquals(128000L, m.spec().get("contextWindow"));
        assertEquals("abc", m.extra().get("customFlag"));
    }

    @Test
    void registeredModelAutoId() {
        Endpoint m = Endpoint.of(ApiKind.GEMINI_OFFICIAL, "gemini-2.5", "http://g", "k");
        assertEquals("GEMINI_OFFICIAL@http://g", m.id());
        assertFalse(m.isDefault());
    }

    @Test
    void registeredModelExtraExcludesCoreFields() {
        String json = """
                {"apiKind":"DEEPSEEK_OFFICIAL","modelId":"deepseek-reasoner",
                 "baseUrl":"http://d","apiKey":"k","extraKey":1}
                """;
        Endpoint m = Endpoint.fromJson(json);
        assertEquals(1L, m.extra().get("extraKey"));
        assertFalse(m.extra().containsKey("apiKind"));
        assertFalse(m.extra().containsKey("modelId"));
        assertFalse(m.extra().containsKey("baseUrl"));
    }

    @Test
    void chatRequestBuilds() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hello"))
                .thinking(ThinkingConfig.of(ThinkingConfig.Mode.ON, ThinkingConfig.Effort.HIGH))
                .sampling(SamplingConfig.of(0.7, 512))
                .build();
        assertEquals(1, req.messages().size());
        assertEquals(ThinkingConfig.Mode.ON, req.thinking().mode());
        assertEquals(0.7, req.sampling().temperature());
        assertEquals(512, req.sampling().maxTokens());
    }

    @Test
    void messageRolesAndBlocks() {
        Message m = new Message(Message.Role.USER, List.of(
                new ContentBlock.Text("look at"),
                new ContentBlock.Image("data:image/png;base64,xxx", "image/png")),
                List.of(), null, null);
        assertEquals(2, m.content().size());
        assertInstanceOf(ContentBlock.Image.class, m.content().get(1));
    }

    @Test
    void thinkingConfig() {
        assertFalse(ThinkingConfig.off().enabled());
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
        assertTrue(new ChatResponse("a", "thought", List.of(), null, "stop", null).isThinking());
        assertFalse(new ChatResponse("a", null, List.of(), null, "stop", null).isThinking());
    }
}
