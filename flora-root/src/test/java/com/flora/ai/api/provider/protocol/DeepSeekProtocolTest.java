package com.flora.ai.api.provider.protocol;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Message;
import com.flora.ai.api.ToolCall;
import com.flora.ai.api.ToolSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DeepSeekProtocol} 序列化/解析测试。
 */
class DeepSeekProtocolTest {

    private static final String MODEL = "deepseek-chat";
    private static final String REASONER_MODEL = "deepseek-reasoner";

    @Test
    void buildRequestBasic() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .build();
        Map<String, Object> body = DeepSeekProtocol.buildRequestMap(req, MODEL, false, null);
        assertEquals("deepseek-chat", body.get("model"));
        assertEquals(1, ((List<?>) body.get("messages")).size());
    }

    @Test
    void buildRequestSystemRolePassthrough() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(
                        Message.of(Message.Role.SYSTEM, "be concise"),
                        Message.of(Message.Role.USER, "hi")))
                .build();
        Map<String, Object> body = DeepSeekProtocol.buildRequestMap(req, MODEL, false, null);
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(2, messages.size());
        assertEquals("system", ((Map<?, ?>) messages.get(0)).get("role"));
        assertEquals("be concise", ((Map<?, ?>) messages.get(0)).get("content"));
    }

    @Test
    void buildRequestJsonObject() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .build();
        Map<String, Object> body = DeepSeekProtocol.buildRequestMap(req, MODEL, false,
                Map.of("type", "json_object"));
        assertEquals("json_object", ((Map<?, ?>) body.get("response_format")).get("type"));
    }

    @Test
    void buildRequestJsonSchemaRejected() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> DeepSeekProtocol.buildRequestMap(req, MODEL, false,
                        Map.of("type", "json_schema")));
    }

    @Test
    void reasonerRejectsTools() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .tool(ToolSpec.of("f", "d", Map.of()))
                .build();
        assertThrows(IllegalArgumentException.class,
                () -> DeepSeekProtocol.buildRequestMap(req, REASONER_MODEL, false, null));
    }

    @Test
    void nonReasonerAllowsTools() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .tool(ToolSpec.of("f", "d", Map.of()))
                .build();
        Map<String, Object> body = DeepSeekProtocol.buildRequestMap(req, MODEL, false, null);
        assertEquals(1, ((List<?>) body.get("tools")).size());
    }

    @Test
    void parseResponseToolCalls() {
        String json = """
                {"choices":[{"message":{"role":"assistant","content":null,
                  "tool_calls":[{"id":"call_1","type":"function",
                    "function":{"name":"get_weather","arguments":"{\\"city\\":\\"beijing\\"}"}}]},
                  "finish_reason":"tool_calls"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5}}
                """;
        var resp = DeepSeekProtocol.parseResponse(json);
        assertTrue(resp.hasToolCalls());
        ToolCall call = resp.toolCalls().get(0);
        assertEquals("call_1", call.id());
        assertEquals("get_weather", call.name());
        assertEquals("beijing", call.arguments().get("city"));
        assertEquals("tool_calls", resp.stopReason());
    }

    @Test
    void parseResponseThinking() {
        String json = """
                {"choices":[{"message":{"role":"assistant","content":"answer",
                  "reasoning_content":"think"},"finish_reason":"stop"}]}
                """;
        var resp = DeepSeekProtocol.parseResponse(json);
        assertEquals("answer", resp.text());
        assertEquals("think", resp.thinkingText());
        assertTrue(resp.isThinking());
    }

    @Test
    void extractStreamDelta() {
        String data = """
                {"choices":[{"delta":{"content":"Hello"}}]}
                """;
        DeepSeekProtocol.Delta delta = DeepSeekProtocol.extractStreamDelta(data);
        assertNotNull(delta);
        assertEquals("Hello", delta.text());
        assertFalse(delta.thinking());
    }
}
