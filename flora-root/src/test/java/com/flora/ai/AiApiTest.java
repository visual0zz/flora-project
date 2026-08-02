package com.flora.ai;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.Capability;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.EndpointClients;
import com.flora.ai.api.Message;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.Tag;
import com.flora.ai.api.spi.TaskContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AiApi} 门面：注册/使用分离 + 端点 client 容器测试。
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
        // Anthropic 官方不支持 JSON，声明了 JSON 能力应报错
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
        assertEquals("mock-answer:mb", AiApi.getDefault().chat().ask(simpleReq()));
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

    // ── 获取 ──

    @Test
    void getByNameReturnsClients() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        EndpointClients clients = AiApi.getByName("mock-1");
        assertEquals("mock-answer:mock-model", clients.chat().chat(simpleReq()).text());
    }

    @Test
    void getByNameWithClass() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        ChatClient c = AiApi.getByName("mock-1", ChatClient.class);
        assertEquals("mock-answer:mock-model", c.ask(simpleReq()));
    }

    @Test
    void getByNameUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> AiApi.getByName("nope"));
    }

    @Test
    void getByNameMissingCapabilityThrows() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        // 该端点未声明 JSON 能力 → 取 json 抛异常
        assertThrows(IllegalStateException.class,
                () -> AiApi.getByName("mock-1").json());
    }

    @Test
    void getDefaultReturnsClients() {
        AiApi.register(endpointJson("d", "default-model", true));
        assertEquals("mock-answer:default-model",
                AiApi.getDefault().chat().chat(simpleReq()).text());
    }

    @Test
    void getDefaultNoDefaultThrows() {
        assertThrows(IllegalStateException.class, AiApi::getDefault);
    }

    @Test
    void getByContextWithoutRouterFallsBackToDefault() {
        AiApi.register(endpointJson("d", "default-model", true));
        assertEquals("mock-answer:default-model",
                AiApi.getByContext(TaskContext.empty()).chat().chat(simpleReq()).text());
    }

    @Test
    void getByContextWithRouter() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.register("{\"id\":\"c\",\"apiKind\":\"OPENAI_LIKE\",\"modelId\":\"chosen-model\"," +
                "\"baseUrl\":\"http://c\",\"capabilities\":[\"CHAT\",\"STREAM\"]," +
                "\"spec\":{\"kind\":\"reasoning\"}}");
        AiApi.setRouter((endpoints, ctx) -> {
            for (Endpoint e : endpoints) {
                if ("reasoning".equals(e.spec().get("kind"))) {
                    return e;
                }
            }
            return null;
        });
        assertEquals("mock-answer:chosen-model",
                AiApi.getByContext(TaskContext.of("need", "reasoning")).chat().chat(simpleReq()).text());
    }

    @Test
    void getByContextRouterReturnsNullFallsBackToDefault() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.setRouter((endpoints, ctx) -> null);
        assertEquals("mock-answer:default-model",
                AiApi.getByContext(TaskContext.empty()).chat().chat(simpleReq()).text());
    }

    @Test
    void getByContextRouterThrowsFallsBackToDefault() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.setRouter((endpoints, ctx) -> {
            throw new RuntimeException("router boom");
        });
        assertEquals("mock-answer:default-model",
                AiApi.getByContext(TaskContext.empty()).chat().chat(simpleReq()).text());
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
        EndpointClients clients = AiApi.getByName("mock-1");
        assertTrue(clients.supports(StreamingClient.class));
        assertEquals("mock-answer", clients.stream().stream(null).collectText());
    }

    @Test
    void clientsContainerRejectsMissing() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        EndpointClients clients = AiApi.getByName("mock-1");
        assertFalse(clients.supports(com.flora.ai.api.JsonClient.class));
        assertThrows(IllegalStateException.class, clients::json);
    }
}
