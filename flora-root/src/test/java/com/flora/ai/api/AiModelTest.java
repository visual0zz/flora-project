package com.flora.ai.api;

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
        var endpoints = Endpoint.fromJsonAll(json);
        assertEquals(1, endpoints.size()); // 默认 CHAT
        Endpoint m = endpoints.get(0);
        assertEquals("gpt:CHAT", m.id());
        assertEquals(ApiSchema.OPENAI_OFFICIAL, m.apiKind());
        assertEquals("gpt-5", m.modelId());
        assertTrue(m.isDefault());
        assertEquals(Capability.CHAT, m.capability());
        assertTrue(m.tags().containsAll(Set.of(Tag.THINKING, Tag.MULTIMODAL)));
        assertEquals(128000L, m.spec().get("contextWindow"));
        assertEquals("abc", m.extra().get("customFlag"));
    }

    @Test
    void registeredModelExpandsCapabilities() {
        String json = """
                {"id":"gpt","apiKind":"OPENAI_OFFICIAL","modelId":"gpt-5",
                 "baseUrl":"https://api.openai.com","apiKey":"sk",
                 "capabilities":["CHAT","STREAM","JSON"]}
                """;
        var endpoints = Endpoint.fromJsonAll(json);
        assertEquals(3, endpoints.size());
        assertEquals("gpt:CHAT", endpoints.get(0).id());
        assertEquals("gpt:STREAM", endpoints.get(1).id());
        assertEquals("gpt:JSON", endpoints.get(2).id());
    }

    @Test
    void registeredModelAutoId() {
        Endpoint m = Endpoint.of(ApiSchema.GEMINI_OFFICIAL, "gemini-2.5", "http://g", "k");
        assertEquals("GEMINI_OFFICIAL@http://g", m.id());
        assertEquals(Capability.CHAT, m.capability());
        assertFalse(m.isDefault());
    }

    @Test
    void registeredModelExtraExcludesCoreFields() {
        String json = """
                {"apiKind":"DEEPSEEK_OFFICIAL","modelId":"deepseek-reasoner",
                 "baseUrl":"http://d","apiKey":"k","extraKey":1}
                """;
        Endpoint m = Endpoint.fromJsonAll(json).get(0);
        assertEquals(1L, m.extra().get("extraKey"));
        assertFalse(m.extra().containsKey("apiKind"));
        assertFalse(m.extra().containsKey("modelId"));
        assertFalse(m.extra().containsKey("baseUrl"));
    }

    @Test
    void chatRequestBuilds() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hello"))
                .config(InferenceConfig.of(512))
                .build();
        assertEquals(1, req.messages().size());
        assertEquals(512, req.config().maxTokens());
        assertEquals(Thinking.AUTO, req.config().thinking());
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
        assertEquals(Thinking.AUTO, InferenceConfig.DEFAULT.thinking());
        assertEquals(Thinking.HIGH, InferenceConfig.thinking(Thinking.HIGH).thinking());
    }

    @Test
    void responseThinkingFlag() {
        assertTrue(new ChatResponse("a", "thought", List.of(), null, "stop", null).isThinking());
        assertFalse(new ChatResponse("a", null, List.of(), null, "stop", null).isThinking());
    }
}
