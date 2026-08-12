package com.flora.root.ai.api.provider.protocol;

import com.flora.root.ai.api.ChatRequest;
import com.flora.root.ai.api.ContentBlock;
import com.flora.root.ai.api.Message;
import com.flora.root.ai.api.InferenceConfig;
import com.flora.root.ai.api.Thinking;
import com.flora.root.ai.api.ToolCall;
import com.flora.root.ai.api.ToolSpec;
import com.flora.root.codec.json.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OpenAiProtocol} 序列化/解析测试（不调外部 API）。
 */
class OpenAiProtocolTest {

    private static final String MODEL = "gpt-5";

    @Test
    void buildRequestBasic() {
        ChatRequest req = ChatRequest.builder()
                .system("be nice")
                .message(Message.of(Message.Role.USER, "hello"))
                .build();
        Map<String, Object> body = OpenAiProtocol.buildRequestMap(req, MODEL, false);
        assertEquals("gpt-5", body.get("model"));
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(2, messages.size());
        Map<?, ?> m0 = (Map<?, ?>) messages.get(0);
        assertEquals("system", m0.get("role"));
        assertEquals("be nice", m0.get("content"));
    }

    @Test
    void buildRequestSystemRolePassthrough() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(
                        Message.of(Message.Role.SYSTEM, "be nice"),
                        Message.of(Message.Role.USER, "hello")))
                .build();
        Map<String, Object> body = OpenAiProtocol.buildRequestMap(req, MODEL, false);
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(2, messages.size());
        assertEquals("system", ((Map<?, ?>) messages.get(0)).get("role"));
        assertEquals("be nice", ((Map<?, ?>) messages.get(0)).get("content"));
        assertEquals("user", ((Map<?, ?>) messages.get(1)).get("role"));
    }

    @Test
    void buildRequestSampling() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .config(InferenceConfig.of(128))
                .build();
        Map<String, Object> body = OpenAiProtocol.buildRequestMap(req, MODEL, false);
        assertFalse(body.containsKey("temperature"));
        assertEquals(128, body.get("max_tokens"));
    }

    @Test
    void buildRequestThinking() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .config(InferenceConfig.thinking(Thinking.HIGH))
                .build();
        Map<String, Object> body = OpenAiProtocol.buildRequestMap(req, MODEL, false);
        assertEquals("high", body.get("reasoning_effort"));
    }

    @Test
    void buildRequestMultimodal() {
        Message m = new Message(Message.Role.USER, List.of(
                new ContentBlock.Text("what is this?"),
                new ContentBlock.Image("data:image/png;base64,abc", "image/png")), List.of(), null, null);
        ChatRequest req = ChatRequest.builder().message(m).build();
        Map<String, Object> body = OpenAiProtocol.buildRequestMap(req, MODEL, false);
        List<?> messages = (List<?>) body.get("messages");
        Map<?, ?> msg = (Map<?, ?>) messages.get(0);
        List<?> content = (List<?>) msg.get("content");
        assertEquals(2, content.size());
        Map<?, ?> imageBlock = (Map<?, ?>) content.get(1);
        assertEquals("image_url", imageBlock.get("type"));
    }

    @Test
    void parseResponse() {
        String json = """
                {"choices":[{"message":{"role":"assistant","content":"answer","reasoning_content":"think"},
                    "finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5}}
                """;
        var resp = OpenAiProtocol.parseResponse(json);
        assertEquals("answer", resp.text());
        assertEquals("think", resp.thinkingText());
        assertTrue(resp.isThinking());
        assertEquals("stop", resp.stopReason());
        assertEquals(10, resp.usage().inputTokens());
        assertEquals(5, resp.usage().outputTokens());
    }

    @Test
    void parseStreamChunkDelta() {
        String chunk = """
                {"choices":[{"delta":{"content":"Hello","reasoning_content":"hmm"}}]}
                """;
        Map<String, Object> root = JsonParser.parseObject(chunk).toMap();
        List<?> choices = (List<?>) root.get("choices");
        Map<?, ?> delta = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("delta");
        assertEquals("Hello", delta.get("content"));
        assertEquals("hmm", delta.get("reasoning_content"));
    }

    // ── tools / response_format ──

    @Test
    void buildRequestTools() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .tool(ToolSpec.of("get_weather", "查询天气",
                        Map.of("type", "object")))
                .build();
        Map<String, Object> body = OpenAiProtocol.buildRequestMap(req, MODEL, false);
        List<?> tools = (List<?>) body.get("tools");
        assertEquals(1, tools.size());
        Map<?, ?> tool = (Map<?, ?>) tools.get(0);
        assertEquals("function", tool.get("type"));
        assertEquals("get_weather", ((Map<?, ?>) tool.get("function")).get("name"));
    }

    @Test
    void buildRequestResponseFormatJsonObject() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .build();
        Map<String, Object> body = OpenAiProtocol.buildRequestMap(req, MODEL, false,
                Map.of("type", "json_object"));
        assertEquals("json_object", ((Map<?, ?>) body.get("response_format")).get("type"));
    }

    @Test
    void buildRequestToolResultMessage() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(
                        Message.of(Message.Role.USER, "weather?"),
                        Message.assistantWithCalls(
                                List.of(ToolCall.of("call_1", "get_weather", Map.of("city", "bj"))), null),
                        Message.toolResult("call_1", "sunny")))
                .build();
        Map<String, Object> body = OpenAiProtocol.buildRequestMap(req, MODEL, false);
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(3, messages.size());
        Map<?, ?> toolMsg = (Map<?, ?>) messages.get(2);
        assertEquals("tool", toolMsg.get("role"));
        assertEquals("call_1", toolMsg.get("tool_call_id"));
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
        var resp = OpenAiProtocol.parseResponse(json);
        assertTrue(resp.hasToolCalls());
        assertEquals("call_1", resp.toolCalls().get(0).id());
        assertEquals("get_weather", resp.toolCalls().get(0).name());
        assertEquals("beijing", resp.toolCalls().get(0).arguments().get("city"));
    }
}
