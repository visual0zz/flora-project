package com.flora.ai;

import com.flora.ai.api.IOMode;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.JsonClient;
import com.flora.ai.api.Message;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.Capability;
import com.flora.ai.api.spi.TaskContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AiApi} 门面：注册展开 + 端点与 client 一对一测试。
 */
class AiApiTest {

    @AfterEach
    void cleanup() {
        for (Endpoint e : AiApi.endpoints()) {
            AiApi.unregister(e.id().substring(0, e.id().indexOf(':')));
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

    // ── 注册：JSON 展开为多 Endpoint ──

    @Test
    void registerExpandsCapabilities() {
        String json = """
                {"id":"mock-1","apiKind":"OPENAI_LIKE","modelId":"mock-model",
                 "baseUrl":"http://mock","apiKey":"k","default":true,
                 "capabilities":["CHAT","STREAM"],
                 "spec":{"contextWindow":128000},"priority":5}
                """;
        var endpoints = AiApi.register(json);
        assertEquals(2, endpoints.size());
        // id 展开为 原id:capability
        assertEquals("mock-1:CHAT", endpoints.get(0).id());
        assertEquals("mock-1:STREAM", endpoints.get(1).id());
        assertEquals(IOMode.CHAT, endpoints.get(0).capability());
        assertEquals(128000L, endpoints.get(0).spec().get("contextWindow"));
        assertEquals(5L, endpoints.get(0).extra().get("priority"));
    }

    @Test
    void registerDefaultsToChat() {
        AiApi.register("{\"id\":\"m\",\"apiKind\":\"OPENAI_LIKE\",\"modelId\":\"x\"," +
                "\"baseUrl\":\"http://m\"}");
        assertEquals(1, AiApi.endpoints().size());
        assertEquals("m:CHAT", AiApi.endpoints().get(0).id());
    }

    @Test
    void registerUnsupportedCapabilityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> AiApi.register("{\"id\":\"x\",\"apiKind\":\"ANTHROPIC_OFFICIAL\"," +
                        "\"modelId\":\"claude\",\"baseUrl\":\"http://x\"," +
                        "\"capabilities\":[\"CHAT\",\"EMBEDDING\"]}"));
    }

    @Test
    void registerAllParsesArray() {
        String jsonArray = "[" + endpointJson("a", "ma", false) + "," + endpointJson("b", "mb", true) + "]";
        var registered = AiApi.registerAll(jsonArray);
        assertEquals(4, registered.size());
        assertEquals(4, AiApi.endpoints().size());
        assertEquals("mock-answer:mb", AiApi.getDefault(ChatClient.class).chat(simpleReq()).text());
    }

    @Test
    void unregisterRemovesAllCapabilities() {
        AiApi.register(endpointJson("m1", "x", false));
        assertEquals(2, AiApi.endpoints().size());
        AiApi.unregister("m1");
        assertEquals(0, AiApi.endpoints().size());
    }

    // ── 获取：展开后 id ──

    @Test
    void getByNameReturnsChatClient() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        ChatClient c = AiApi.getByName("mock-1:CHAT", ChatClient.class);
        assertEquals("mock-answer:mock-model", c.chat(simpleReq()).text());
    }

    @Test
    void getByNameReturnsStreamClient() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        StreamingClient s = AiApi.getByName("mock-1:STREAM", StreamingClient.class);
        assertEquals("mock-answer", s.stream(null).collectText());
    }

    @Test
    void getByNameUnknownThrows() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        assertThrows(IllegalArgumentException.class,
                () -> AiApi.getByName("nope:CHAT", ChatClient.class));
    }

    @Test
    void getByNameMissingCapabilityThrows() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        // 该端点未声明 JSON 能力
        assertThrows(IllegalArgumentException.class,
                () -> AiApi.getByName("mock-1:JSON", JsonClient.class));
    }

    @Test
    void getDefaultReturnsChatClient() {
        AiApi.register(endpointJson("d", "default-model", true));
        assertEquals("mock-answer:default-model",
                AiApi.getDefault(ChatClient.class).chat(simpleReq()).text());
    }

    @Test
    void getDefaultNoDefaultThrows() {
        assertThrows(IllegalStateException.class, () -> AiApi.getDefault(ChatClient.class));
    }

    @Test
    void getByContextWithoutRouterFallsBackToDefaultChat() {
        AiApi.register(endpointJson("d", "default-model", true));
        ChatClient c = AiApi.getByContext(TaskContext.empty());
        assertEquals("mock-answer:default-model", c.chat(simpleReq()).text());
    }

    @Test
    void getByContextWithRouterReturnsEndpoint() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.register("{\"id\":\"c\",\"apiKind\":\"OPENAI_LIKE\",\"modelId\":\"chosen-model\"," +
                "\"baseUrl\":\"http://c\",\"capabilities\":[\"CHAT\",\"STREAM\"]," +
                "\"spec\":{\"kind\":\"reasoning\"}}");
        // Router 读 ctx，返回匹配的 Endpoint（已含能力）
        AiApi.setRouter((endpoints, ctx) -> {
            for (Endpoint e : endpoints) {
                if ("reasoning".equals(e.spec().get("kind"))
                        && e.capability() == IOMode.CHAT) {
                    return e;
                }
            }
            return null;
        });
        ChatClient c = AiApi.getByContext(TaskContext.of("need", "reasoning"));
        assertEquals("mock-answer:chosen-model", c.chat(simpleReq()).text());
    }

    @Test
    void getByContextRouterReturnsStreamEndpoint() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.register("{\"id\":\"c\",\"apiKind\":\"OPENAI_LIKE\",\"modelId\":\"chosen-model\"," +
                "\"baseUrl\":\"http://c\",\"capabilities\":[\"CHAT\",\"STREAM\"]," +
                "\"spec\":{\"kind\":\"reasoning\"}}");
        AiApi.setRouter((endpoints, ctx) -> {
            for (Endpoint e : endpoints) {
                if ("reasoning".equals(e.spec().get("kind"))
                        && e.capability() == IOMode.STREAM) {
                    return e;
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
        assertEquals("mock-answer:default-model", c.chat(simpleReq()).text());
    }

    @Test
    void getByContextRouterThrowsFallsBackToDefaultChat() {
        AiApi.register(endpointJson("d", "default-model", true));
        AiApi.setRouter((endpoints, ctx) -> {
            throw new RuntimeException("router boom");
        });
        ChatClient c = AiApi.getByContext(TaskContext.empty());
        assertEquals("mock-answer:default-model", c.chat(simpleReq()).text());
    }

    @Test
    void getByContextNoEndpointsThrows() {
        assertThrows(IllegalStateException.class,
                () -> AiApi.getByContext(TaskContext.empty()));
    }

    @Test
    void eachCapabilityIsSeparateEndpoint() {
        AiApi.register(endpointJson("mock-1", "mock-model", false));
        // CHAT 与 STREAM 是独立 Endpoint（独立实例）
        assertNotSame(AiApi.getByName("mock-1:CHAT", ChatClient.class),
                AiApi.getByName("mock-1:STREAM", StreamingClient.class));
        // 同能力同 id 是同一预建实例
        assertSame(AiApi.getByName("mock-1:CHAT", ChatClient.class),
                AiApi.getByName("mock-1:CHAT", ChatClient.class));
    }
}
