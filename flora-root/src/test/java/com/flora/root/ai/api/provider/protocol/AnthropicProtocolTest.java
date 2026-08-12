package com.flora.root.ai.api.provider.protocol;

import com.flora.root.ai.api.ChatRequest;
import com.flora.root.ai.api.ContentBlock;
import com.flora.root.ai.api.Message;
import com.flora.root.ai.api.InferenceConfig;
import com.flora.root.ai.api.Thinking;
import com.flora.root.ai.api.ToolCall;
import com.flora.root.ai.api.ToolSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AnthropicProtocol} 序列化/解析测试（不调外部 API）。
 */
class AnthropicProtocolTest {

    private static final String MODEL = "claude-sonnet-4";

    @Test
    void buildRequestSystemTopLevel() {
        ChatRequest req = ChatRequest.builder()
                .system("you are claude")
                .message(Message.of(Message.Role.USER, "hi"))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false, null);
        assertEquals("you are claude", body.get("system"));
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(1, messages.size(), "system 字段不应出现在 messages");
        assertEquals("user", ((Map<?, ?>) messages.get(0)).get("role"));
    }

    @Test
    void buildRequestSystemRoleMergedToTopLevel() {
        ChatRequest req = ChatRequest.builder()
                .system("top level")
                .messages(List.of(
                        Message.of(Message.Role.SYSTEM, "role level"),
                        Message.of(Message.Role.USER, "hi")))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false, null);
        assertEquals("top level\n\nrole level", body.get("system"), "顶层字段与 SYSTEM 消息按序合并");
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(1, messages.size(), "SYSTEM 角色消息不应出现在 messages");
        assertEquals("user", ((Map<?, ?>) messages.get(0)).get("role"));
    }

    @Test
    void buildRequestDefaultMaxTokens() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false, null);
        assertEquals(AnthropicProtocol.DEFAULT_MAX_TOKENS, body.get("max_tokens"));
    }

    @Test
    void buildRequestThinking() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .config(InferenceConfig.thinking(Thinking.HIGH))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false, null);
        Map<?, ?> thinking = (Map<?, ?>) body.get("thinking");
        assertNotNull(thinking);
        assertEquals("enabled", thinking.get("type"));
        assertEquals("high", thinking.get("effort"));
    }

    @Test
    void parseResponseThinkingBlock() {
        String json = """
                {"content":[{"type":"thinking","thinking":"reasoning..."},
                            {"type":"text","text":"final answer"}],
                 "stop_reason":"end_turn",
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """;
        var resp = AnthropicProtocol.parseResponse(json);
        assertEquals("final answer", resp.text());
        assertEquals("reasoning...", resp.thinkingText());
        assertTrue(resp.isThinking());
        assertEquals("end_turn", resp.stopReason());
    }

    @Test
    void extractStreamDeltaText() {
        String data = """
                {"type":"content_block_delta","delta":{"type":"text_delta","text":"Hello"}}
                """;
        AnthropicProtocol.Delta delta = AnthropicProtocol.extractStreamDelta(data);
        assertNotNull(delta);
        assertEquals("Hello", delta.text());
        assertFalse(delta.thinking());
    }

    @Test
    void extractStreamDeltaThinking() {
        String data = """
                {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"hmm"}}
                """;
        AnthropicProtocol.Delta delta = AnthropicProtocol.extractStreamDelta(data);
        assertNotNull(delta);
        assertEquals("hmm", delta.text());
        assertTrue(delta.thinking());
    }

    @Test
    void extractStreamDeltaNonDeltaReturnsNull() {
        String data = """
                {"type":"message_start","message":{"role":"assistant"}}
                """;
        assertNull(AnthropicProtocol.extractStreamDelta(data));
    }

    @Test
    void buildRequestToolsUsesInputSchema() {
        ToolSpec tool = ToolSpec.of("getWeather", "get weather", Map.of("type", "object"));
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .tools(List.of(tool))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false, null);
        List<?> tools = (List<?>) body.get("tools");
        assertEquals(1, tools.size());
        Map<?, ?> t = (Map<?, ?>) tools.get(0);
        assertEquals("getWeather", t.get("name"));
        assertTrue(t.containsKey("input_schema"), "Anthropic 用 input_schema 而非 parameters");
        assertFalse(t.containsKey("parameters"));
    }

    @Test
    void buildRequestToolConversationShapes() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(
                        Message.of(Message.Role.USER, "weather?"),
                        Message.assistantWithCalls(
                                List.of(ToolCall.of("call_1", "getWeather", Map.of("city", "BJ"))), "let me check"),
                        Message.toolResult("call_1", "sunny")))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false, null);
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(3, messages.size());
        // assistant 含 tool_use 块
        Map<?, ?> assistant = (Map<?, ?>) messages.get(1);
        assertEquals("assistant", assistant.get("role"));
        List<?> aBlocks = (List<?>) assistant.get("content");
        Map<?, ?> toolUse = (Map<?, ?>) aBlocks.get(1);
        assertEquals("tool_use", toolUse.get("type"));
        assertEquals("call_1", toolUse.get("id"));
        // 工具结果 role=user + tool_result
        Map<?, ?> result = (Map<?, ?>) messages.get(2);
        assertEquals("user", result.get("role"));
        Map<?, ?> tr = (Map<?, ?>) ((List<?>) result.get("content")).get(0);
        assertEquals("tool_result", tr.get("type"));
        assertEquals("call_1", tr.get("tool_use_id"));
    }

    @Test
    void buildRequestToolResultMultimodalContent() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(
                        Message.of(Message.Role.USER, "weather?"),
                        Message.assistantWithCalls(
                                List.of(ToolCall.of("call_1", "capture", Map.of())), null),
                        new Message(Message.Role.TOOL,
                                List.of(new ContentBlock.Text("screenshot:"),
                                        new ContentBlock.Image("data:image/png;base64,xyz", "image/png")),
                                List.of(), "call_1", null)))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false, null);
        Map<?, ?> result = (Map<?, ?>) ((List<?>) body.get("messages")).get(2);
        Map<?, ?> tr = (Map<?, ?>) ((List<?>) result.get("content")).get(0);
        assertEquals("tool_result", tr.get("type"));
        List<?> content = (List<?>) tr.get("content");
        assertEquals(2, content.size(), "多块结果应序列化为内容块数组");
        Map<?, ?> textBlock = (Map<?, ?>) content.get(0);
        assertEquals("text", textBlock.get("type"));
        assertEquals("screenshot:", textBlock.get("text"));
        Map<?, ?> imgBlock = (Map<?, ?>) content.get(1);
        assertEquals("image", imgBlock.get("type"));
        Map<?, ?> source = (Map<?, ?>) imgBlock.get("source");
        assertEquals("base64", source.get("type"));
        assertEquals("image/png", source.get("media_type"));
        assertEquals("xyz", source.get("data"), "dataUrl 前缀应剥离");
    }

    @Test
    void buildRequestToolResultErrorFlag() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(
                        Message.of(Message.Role.USER, "weather?"),
                        Message.assistantWithCalls(
                                List.of(ToolCall.of("call_1", "getWeather", Map.of("city", "BJ"))), null),
                        Message.toolResult("call_1", "boom", true)))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false, null);
        Map<?, ?> result = (Map<?, ?>) ((List<?>) body.get("messages")).get(2);
        Map<?, ?> tr = (Map<?, ?>) ((List<?>) result.get("content")).get(0);
        assertEquals("tool_result", tr.get("type"));
        assertEquals(Boolean.TRUE, tr.get("is_error"), "执行失败应标记 is_error=true");
    }

    @Test
    void buildRequestJsonModeForcesTool() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "give me json"))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false,
                Map.of("type", "json_object"));
        List<?> tools = (List<?>) body.get("tools");
        Map<?, ?> forced = (Map<?, ?>) tools.get(tools.size() - 1);
        assertEquals(AnthropicProtocol.FORCED_TOOL_NAME, forced.get("name"));
        Map<?, ?> choice = (Map<?, ?>) body.get("tool_choice");
        assertEquals("tool", choice.get("type"));
        assertEquals(AnthropicProtocol.FORCED_TOOL_NAME, choice.get("name"));
    }

    @Test
    void parseResponseToolUse() {
        String json = """
                {"content":[{"type":"tool_use","id":"t1","name":"getWeather","input":{"city":"BJ"}},
                            {"type":"text","text":"done"}],
                 "stop_reason":"tool_use",
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """;
        var resp = AnthropicProtocol.parseResponse(json);
        assertEquals("done", resp.text());
        assertEquals(1, resp.toolCalls().size());
        ToolCall tc = resp.toolCalls().get(0);
        assertEquals("t1", tc.id());
        assertEquals("getWeather", tc.name());
        assertEquals("BJ", tc.arguments().get("city"));
        assertEquals("tool_use", resp.stopReason());
    }
}
