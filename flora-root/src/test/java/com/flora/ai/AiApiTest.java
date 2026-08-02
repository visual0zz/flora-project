package com.flora.ai;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.Capability;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ClientSpec;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.JsonClient;
import com.flora.ai.api.Message;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.Tag;
import com.flora.ai.api.spi.TaskContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AiApi} 门面：注册/使用分离 + 返回单个 client 测试。
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
                "\"baseUrl\":\"http://" + id + "\"" +
                ",\"capabilities\":[\"CHAT\",\"STREAM\"]" +
                (isDefault ? ",\"default\":true" : "") + "}";
    }

    // ── provider ──

    @Test
    void loadsProvidersViaCodeAndSpi() {
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("openai")));
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("anthropic")));
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("gemini")));
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("deepseek")));
        assertTrue(AiApi.providers().stream().anyMatch(p -> p.name().equals("mock")));
    }

    // ── 注册 ──

    @Test
    void registerJsonAddsEndpoint() {
        String json = """
                {"id":"mock-1","apiKind":"OPENAI_LIKE","modelId":"mock-model",
                 "baseUrl":"http://mock","apiKey":"k","default":true,
                 "capabilities":["CHAT","STREAM"],
                 "tags":["THINKING","JSON_MODE"],"spec":{"contextWindow":128000},
                 "priority":5}
                """;
        Endpoint e = AiApi.register(json);
        assertEquals("mock-1", e.id());
        assertEquals("mock-model", e.modelId());
        assertTrue(e.isDefault());
        assertTrue(e.capabilities().contains(Capability.CHAT));
        assertTrue(e.tags().contains(Tag.THINKING));
        assertEquals(128000L, e.spec().get("contextWindow"));
        assertEquals(5L, e.extra().get("priority"), "附加字段应保留到 extra");
    }

    @Test
    void registerUnsupportedCapabilityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> AiApi.register("{\"id\":\"x\",\"apiKind\":\"ANTHROPIC_OFFICIAL\"," +
                        "\"modelId\":\"claude\",\"baseUrl\":\"http://x\"," +
                        "\"capabilities\":[\"CHAT\",\"JSON\"]}"));
    }

    @Test
    void registerAllParsesArray() {
        String jsonArray = "[" + endpointJson("a", "ma", false) + "," + endpointJson("b", "mb", true) + "]";
        var registered = AiApi.registerAll(jsonArray);
        assertEquals(2, registered.size());
        assertEquals(2, AiApi.endpoints().size());
        assertEquals("mock-answer:mb", AiApi.getDefault(ChatClient.class).ask(simpleReq()));
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

    // ── 获取：返回单个 client ──

    @Test
    void getByNameReturnsChatClient() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        ChatClient c = AiApi.getByName("mock-1", ChatClient.class);
        assertEquals("mock-answer:mock-model", c.ask(simpleReq()));
    }

    @Test
    void getByNameReturnsStreamClient() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        StreamingClient s = AiApi.getByName("mock-1", StreamingClient.class);
        assertEquals("mock-answer", s.stream(null).collectText());
    }

    @Test
    void getByNameUnknownThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> AiApi.getByName("nope", ChatClient.class));
    }

    @Test
    void getByNameMissingCapabilityThrows() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        // 该端点未声明 JSON 能力
        assertThrows(IllegalArgumentException.class,
                () -> AiApi.getByName("mock-1", JsonClient.class));
    }

    @Test
    void getDefaultReturnsChatClient() {
        AiApi.register(endpointJson("d", "default-model", true));
        assertEquals("mock-answer:default-model",
                AiApi.getDefault(ChatClient.class).ask(simpleReq()));
    }

    @Test
    void getDefaultNoDefaultThrows() {
        assertThrows(IllegalStateException.class, () -> AiApi.getDefault(ChatClient.class));
    }

    @Test
    void getByContextWithoutRouterFallsBackToDefaultChat() {
        AiApi.register(endpointJson("d", "default-model", true));
        ChatClient c = AiApi.getByContext(TaskContext.empty());
        assertEquals("mock-answer:default-model", c.ask(simpleReq()));
    }

    @Test
    void getByContextWithRouterReturnsChat() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.register("{\"id\":\"c\",\"apiKind\":\"OPENAI_LIKE\",\"modelId\":\"chosen-model\"," +
                "\"baseUrl\":\"http://c\",\"capabilities\":[\"CHAT\",\"STREAM\"]," +
                "\"spec\":{\"kind\":\"reasoning\"}}");
        AiApi.setRouter((endpoints, ctx) -> {
            for (Endpoint e : endpoints) {
                if ("reasoning".equals(e.spec().get("kind"))) {
                    return ClientSpec.of(e, Capability.CHAT);
                }
            }
            return null;
        });
        ChatClient c = AiApi.getByContext(TaskContext.of("need", "reasoning"));
        assertEquals("mock-answer:chosen-model", c.ask(simpleReq()));
    }

    @Test
    void getByContextWithRouterReturnsStream() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.register("{\"id\":\"c\",\"apiKind\":\"OPENAI_LIKE\",\"modelId\":\"chosen-model\"," +
                "\"baseUrl\":\"http://c\",\"capabilities\":[\"CHAT\",\"STREAM\"]," +
                "\"spec\":{\"kind\":\"reasoning\"}}");
        // Router 读 ctx 能力信息，返回 STREAM 能力的 ClientSpec
        AiApi.setRouter((endpoints, ctx) -> {
            for (Endpoint e : endpoints) {
                if ("reasoning".equals(e.spec().get("kind"))) {
                    return ClientSpec.of(e, Capability.STREAM);
                }
            }
            return null;
        });
        StreamingClient s = AiApi.getByContext(TaskContext.of("capability", "STREAM"));
        assertEquals("mock-answer", s.stream(null).collectText());
    }

    @Test
    void getByContextRouterReturnsNullFallsBackToDefaultChat() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.setRouter((endpoints, ctx) -> null);
        ChatClient c = AiApi.getByContext(TaskContext.empty());
        assertEquals("mock-answer:default-model", c.ask(simpleReq()));
    }

    @Test
    void getByContextRouterThrowsFallsBackToDefaultChat() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.setRouter((endpoints, ctx) -> {
            throw new RuntimeException("router boom");
        });
        ChatClient c = AiApi.getByContext(TaskContext.empty());
        assertEquals("mock-answer:default-model", c.ask(simpleReq()));
    }

    @Test
    void getByContextNoEndpointsThrows() {
        assertThrows(IllegalStateException.class,
                () -> AiApi.getByContext(TaskContext.empty()));
    }

    @Test
    void getByContextTypeMismatchThrows() {
        AiApi.register(endpointJson("d", "default-model", true));
        // Router 返回 CHAT 的 ClientSpec，但调用方声明 StreamingClient
        AiApi.setRouter((endpoints, ctx) ->
                ClientSpec.of(endpoints.get(0), Capability.CHAT));
        // 泛型 T 在赋值给 StreamingClient 时 JVM checkcast → ClassCastException
        assertThrows(ClassCastException.class, () -> {
            StreamingClient s = AiApi.getByContext(TaskContext.empty());
            s.toString();
        });
    }
}
