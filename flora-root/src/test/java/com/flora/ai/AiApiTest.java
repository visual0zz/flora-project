package com.flora.ai;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Message;
import com.flora.ai.api.RegisteredModel;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.Tag;
import com.flora.ai.api.spi.TaskContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AiApi} 门面：注册/目录/创建/Router 测试。
 */
class AiApiTest {

    @AfterEach
    void cleanup() {
        for (RegisteredModel m : AiApi.models()) {
            AiApi.unregister(m.id());
        }
        AiApi.setRouter(null);
    }

    private ChatRequest simpleReq() {
        return ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .build();
    }

    @Test
    void loadsProvidersViaCodeAndSpi() {
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("openai")));
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("anthropic")));
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("gemini")));
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("deepseek")));
        // mock 绑定 OPENAI_COMPATIBLE 且经 SPI 附加，会顶替内置 openai-compatible（同 ApiKind）
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("mock")));
        assertEquals(ApiKind.OPENAI_COMPATIBLE, AiApi.providers().stream()
                .filter(p -> p.name().equals("mock")).findFirst().orElseThrow().apiKind());
    }

    @Test
    void registerJsonAddsModel() {
        String json = """
                {"id":"mock-1","apiKind":"OPENAI_COMPATIBLE","modelId":"mock-model",
                 "baseUrl":"http://mock","apiKey":"k","default":true,
                 "tags":["THINKING","JSON_MODE"],"spec":{"contextWindow":128000},
                 "priority":5}
                """;
        RegisteredModel m = AiApi.register(json);
        assertEquals("mock-1", m.id());
        assertEquals("mock-model", m.modelId());
        assertTrue(m.isDefault());
        assertTrue(m.tags().contains(Tag.THINKING));
        assertEquals(128000L, m.spec().get("contextWindow"));
        assertEquals(5L, m.extra().get("priority"), "附加字段应保留到 extra");
    }

    @Test
    void defaultMustBeUnique() {
        AiApi.register("{\"id\":\"a\",\"apiKind\":\"OPENAI_COMPATIBLE\",\"modelId\":\"m1\"," +
                "\"baseUrl\":\"http://a\",\"default\":true}");
        assertThrows(IllegalArgumentException.class,
                () -> AiApi.register("{\"id\":\"b\",\"apiKind\":\"OPENAI_COMPATIBLE\",\"modelId\":\"m2\"," +
                        "\"baseUrl\":\"http://b\",\"default\":true}"));
    }

    @Test
    void modelsListsRegistered() {
        AiApi.register("{\"id\":\"m1\",\"apiKind\":\"OPENAI_COMPATIBLE\",\"modelId\":\"x\"," +
                "\"baseUrl\":\"http://x\"}");
        AiApi.register("{\"id\":\"m2\",\"apiKind\":\"OPENAI_COMPATIBLE\",\"modelId\":\"y\"," +
                "\"baseUrl\":\"http://y\",\"default\":true}");
        assertEquals(2, AiApi.models().size());
        assertEquals("m2", AiApi.defaultModel().id());
    }

    @Test
    void clientCreatedFromRegisteredModel() {
        RegisteredModel m = AiApi.register("{\"id\":\"mock-1\",\"apiKind\":\"OPENAI_COMPATIBLE\"," +
                "\"modelId\":\"mock-model\",\"baseUrl\":\"http://mock\"}");
        ChatClient client = AiApi.client(m);
        assertEquals("mock-answer:mock-model", client.chat(simpleReq()).text());
    }

    @Test
    void clientCreatedByKind() {
        ChatClient client = AiApi.client(ApiKind.OPENAI_COMPATIBLE, "http://mock", "k");
        assertEquals("mock-answer:model", client.chat(simpleReq()).text());
    }

    @Test
    void routedWithoutRouterFallsBackToDefault() {
        AiApi.register("{\"id\":\"d\",\"apiKind\":\"OPENAI_COMPATIBLE\",\"modelId\":\"default-model\"," +
                "\"baseUrl\":\"http://d\",\"default\":true}");
        ChatClient client = AiApi.routed(TaskContext.empty());
        assertEquals("mock-answer:default-model", client.chat(simpleReq()).text());
    }

    @Test
    void routedWithRouter() {
        AiApi.register("{\"id\":\"d\",\"apiKind\":\"OPENAI_COMPATIBLE\",\"modelId\":\"default-model\"," +
                "\"baseUrl\":\"http://d\",\"default\":true}");
        AiApi.register("{\"id\":\"c\",\"apiKind\":\"OPENAI_COMPATIBLE\",\"modelId\":\"chosen-model\"," +
                "\"baseUrl\":\"http://c\",\"spec\":{\"kind\":\"reasoning\"}}");
        AiApi.setRouter((models, ctx) -> {
            for (RegisteredModel m : models) {
                if ("reasoning".equals(m.spec().get("kind"))) {
                    return m;
                }
            }
            return null;
        });
        ChatClient client = AiApi.routed(TaskContext.of("need", "reasoning"));
        assertEquals("mock-answer:chosen-model", client.chat(simpleReq()).text());
    }

    @Test
    void routedRouterReturnsNullFallsBackToDefault() {
        AiApi.register("{\"id\":\"d\",\"apiKind\":\"OPENAI_COMPATIBLE\",\"modelId\":\"default-model\"," +
                "\"baseUrl\":\"http://d\",\"default\":true}");
        AiApi.setRouter((models, ctx) -> null);
        assertEquals("mock-answer:default-model",
                AiApi.routed(TaskContext.empty()).chat(simpleReq()).text());
    }

    @Test
    void routedNoModelsThrows() {
        assertThrows(IllegalStateException.class, () -> AiApi.routed(TaskContext.empty()));
    }

    @Test
    void streamingCapabilityDetected() {
        RegisteredModel m = AiApi.register("{\"id\":\"mock-1\",\"apiKind\":\"OPENAI_COMPATIBLE\"," +
                "\"modelId\":\"mock-model\",\"baseUrl\":\"http://mock\"}");
        ChatClient client = AiApi.client(m);
        assertInstanceOf(StreamingClient.class, client);
        assertEquals("mock-answer", ((StreamingClient) client).stream(null).collectText());
    }

    @Test
    void unregisterRemovesModel() {
        AiApi.register("{\"id\":\"m1\",\"apiKind\":\"OPENAI_COMPATIBLE\",\"modelId\":\"x\"," +
                "\"baseUrl\":\"http://x\"}");
        assertEquals(1, AiApi.models().size());
        AiApi.unregister("m1");
        assertEquals(0, AiApi.models().size());
    }

    @Test
    void clientForOfficialKindUsesBuiltinProvider() {
        RegisteredModel m = RegisteredModel.of(ApiKind.ANTHROPIC_OFFICIAL, "claude", "http://x", "k");
        assertNotNull(AiApi.client(m));
    }
}
