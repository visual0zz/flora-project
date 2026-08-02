package com.flora.ai;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Message;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.Tag;
import com.flora.ai.api.spi.TaskContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AiApi} 门面：注册/使用分离 + 三个获取接口测试。
 */
class AiApiTest {

    @AfterEach
    void cleanup() {
        for (Endpoint e : AiApi.endpoints()) {
            AiApi.unregister(e.id());
        }
        AiApi.setRouter(null);
    }

    private ChatRequest simpleReq() {
        return ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .build();
    }

    private String endpointJson(String id, String modelId, boolean isDefault) {
        return "{\"id\":\"" + id + "\",\"apiKind\":\"OPENAI_LIKE\",\"modelId\":\"" + modelId + "\"," +
                "\"baseUrl\":\"http://" + id + "\"" + (isDefault ? ",\"default\":true" : "") + "}";
    }

    // ── provider ──

    @Test
    void loadsProvidersViaCodeAndSpi() {
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("openai")));
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("anthropic")));
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("gemini")));
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("deepseek")));
        // mock 绑定 OPENAI_LIKE 且经 SPI 附加，会顶替内置 openai-compatible（同 ApiKind）
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("mock")));
    }

    // ── 注册 ──

    @Test
    void registerJsonAddsEndpoint() {
        String json = """
                {"id":"mock-1","apiKind":"OPENAI_LIKE","modelId":"mock-model",
                 "baseUrl":"http://mock","apiKey":"k","default":true,
                 "tags":["THINKING","JSON_MODE"],"spec":{"contextWindow":128000},
                 "priority":5}
                """;
        Endpoint e = AiApi.register(json);
        assertEquals("mock-1", e.id());
        assertEquals("mock-model", e.modelId());
        assertTrue(e.isDefault());
        assertTrue(e.tags().contains(Tag.THINKING));
        assertEquals(128000L, e.spec().get("contextWindow"));
        assertEquals(5L, e.extra().get("priority"), "附加字段应保留到 extra");
    }

    @Test
    void registerAllParsesArray() {
        String jsonArray = "[" + endpointJson("a", "ma", false) + "," + endpointJson("b", "mb", true) + "]";
        var registered = AiApi.registerAll(jsonArray);
        assertEquals(2, registered.size());
        assertEquals(2, AiApi.endpoints().size());
        // b 为 default，getDefault 返回其 client
        assertEquals("mock-answer:mb", AiApi.getDefault().ask(simpleReq()));
    }

    @Test
    void defaultMustBeUnique() {
        AiApi.register(endpointJson("a", "m1", true));
        assertThrows(IllegalArgumentException.class,
                () -> AiApi.register(endpointJson("b", "m2", true)));
    }

    @Test
    void unregisterRemovesEndpoint() {
        AiApi.register(endpointJson("m1", "x", false));
        assertEquals(1, AiApi.endpoints().size());
        AiApi.unregister("m1");
        assertEquals(0, AiApi.endpoints().size());
    }

    // ── 三个获取接口 ──

    @Test
    void getByNameReturnsRegistered() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        ChatClient client = AiApi.getByName("mock-1");
        assertEquals("mock-answer:mock-model", client.chat(simpleReq()).text());
    }

    @Test
    void getByNameUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> AiApi.getByName("nope"));
    }

    @Test
    void getDefaultReturnsDefault() {
        AiApi.register(endpointJson("d", "default-model", true));
        assertEquals("mock-answer:default-model",
                AiApi.getDefault().chat(simpleReq()).text());
    }

    @Test
    void getDefaultNoDefaultThrows() {
        assertThrows(IllegalStateException.class, AiApi::getDefault);
    }

    @Test
    void getByContextWithoutRouterFallsBackToDefault() {
        AiApi.register(endpointJson("d", "default-model", true));
        assertEquals("mock-answer:default-model",
                AiApi.getByContext(TaskContext.empty()).chat(simpleReq()).text());
    }

    @Test
    void getByContextWithRouter() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.register("{\"id\":\"c\",\"apiKind\":\"OPENAI_LIKE\",\"modelId\":\"chosen-model\"," +
                "\"baseUrl\":\"http://c\",\"spec\":{\"kind\":\"reasoning\"}}");
        AiApi.setRouter((endpoints, ctx) -> {
            for (Endpoint e : endpoints) {
                if ("reasoning".equals(e.spec().get("kind"))) {
                    return e;
                }
            }
            return null;
        });
        assertEquals("mock-answer:chosen-model",
                AiApi.getByContext(TaskContext.of("need", "reasoning")).chat(simpleReq()).text());
    }

    @Test
    void getByContextRouterReturnsNullFallsBackToDefault() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.setRouter((endpoints, ctx) -> null);
        assertEquals("mock-answer:default-model",
                AiApi.getByContext(TaskContext.empty()).chat(simpleReq()).text());
    }

    @Test
    void getByContextRouterThrowsFallsBackToDefault() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.setRouter((endpoints, ctx) -> {
            throw new RuntimeException("router boom");
        });
        assertEquals("mock-answer:default-model",
                AiApi.getByContext(TaskContext.empty()).chat(simpleReq()).text());
    }

    @Test
    void getByContextNoEndpointsThrows() {
        assertThrows(IllegalStateException.class,
                () -> AiApi.getByContext(TaskContext.empty()));
    }

    // ── 能力发现 ──

    @Test
    void streamingCapabilityDetected() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        ChatClient client = AiApi.getByName("mock-1");
        assertInstanceOf(StreamingClient.class, client);
        assertEquals("mock-answer", ((StreamingClient) client).stream(null).collectText());
    }
}
