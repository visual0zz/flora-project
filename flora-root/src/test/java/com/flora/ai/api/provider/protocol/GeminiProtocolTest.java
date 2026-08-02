package com.flora.ai.api.provider.protocol;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ContentBlock;
import com.flora.ai.api.Message;
import com.flora.ai.api.ToolCall;
import com.flora.ai.api.ToolSpec;
import com.flora.ai.api.provider.protocol.GeminiProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GeminiProtocol} 序列化/解析测试（不调外部 API）。
 */
class GeminiProtocolTest {

    private static final String MODEL = "gemini-2.5-pro";

    @Test
    void buildRequestPartsWithRoleMapping() {
        ChatRequest req = ChatRequest.builder()
                .system("you are gemini")
                .messages(List.of(
                        Message.of(Message.Role.USER, "hello"),
                        Message.of(Message.Role.ASSISTANT, "hi there")))
                .build();
        Map<String, Object> body = GeminiProtocol.buildRequestMap(req, null);
        // system 应进顶层 systemInstruction
        Map<?, ?> si = (Map<?, ?>) body.get("systemInstruction");
        assertNotNull(si);
        List<?> contents = (List<?>) body.get("contents");
        assertEquals(2, contents.size());
        assertEquals("user", ((Map<?, ?>) contents.get(0)).get("role"));
        assertEquals("model", ((Map<?, ?>) contents.get(1)).get("role"), "assistant 映射为 model");
    }

    @Test
    void buildRequestImageInlineData() {
        Message m = new Message(Message.Role.USER, List.of(
                new ContentBlock.Text("look"),
                new ContentBlock.Image("data:image/png;base64,xyz", "image/png")), List.of(), null, null);
        ChatRequest req = ChatRequest.builder().message(m).build();
        Map<String, Object> body = GeminiProtocol.buildRequestMap(req, null);
        List<?> contents = (List<?>) body.get("contents");
        List<?> parts = (List<?>) ((Map<?, ?>) contents.get(0)).get("parts");
        assertEquals(2, parts.size());
        Map<?, ?> inline = (Map<?, ?>) ((Map<?, ?>) parts.get(1)).get("inline_data");
        assertNotNull(inline);
        assertEquals("image/png", inline.get("mime_type"));
        assertEquals("xyz", inline.get("data"), "dataUrl 前缀应剥离");
    }

    @Test
    void parseResponse() {
        String json = """
                {"candidates":[{"content":{"parts":[
                    {"text":"reasoning","thought":true},
                    {"text":"final answer"}],
                    "role":"model"},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5}}
                """;
        var resp = GeminiProtocol.parseResponse(json);
        assertEquals("final answer", resp.text());
        assertEquals("reasoning", resp.thinkingText());
        assertTrue(resp.isThinking());
        assertEquals(10, resp.usage().inputTokens());
    }

    @Test
    void extractStreamDelta() {
        String data = """
                {"candidates":[{"content":{"parts":[{"text":"delta"}]}}]}
                """;
        assertEquals("delta", GeminiProtocol.extractStreamDelta(data));
    }

    @Test
    void buildRequestToolsUsesFunctionDeclarations() {
        ToolSpec tool = ToolSpec.of("getWeather", "get weather", Map.of("type", "object"));
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .tools(List.of(tool))
                .build();
        Map<String, Object> body = GeminiProtocol.buildRequestMap(req, null);
        List<?> tools = (List<?>) body.get("tools");
        assertEquals(1, tools.size());
        Map<?, ?> declWrapper = (Map<?, ?>) tools.get(0);
        assertTrue(declWrapper.containsKey("functionDeclarations"), "Gemini 用 functionDeclarations 包裹");
        List<?> fns = (List<?>) declWrapper.get("functionDeclarations");
        Map<?, ?> f = (Map<?, ?>) fns.get(0);
        assertEquals("getWeather", f.get("name"));
        assertEquals("get weather", f.get("description"));
        assertTrue(f.containsKey("parameters"), "Gemini 用 parameters 而非 input_schema");
        assertFalse(f.containsKey("input_schema"));
    }

    @Test
    void buildRequestToolConversationShapes() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(
                        Message.of(Message.Role.USER, "weather?"),
                        Message.assistantWithCalls(
                                List.of(ToolCall.of("call_1", "getWeather", Map.of("city", "BJ"))), "let me check"),
                        // TOOL 回执（带 name 以匹配 functionResponse）
                        new Message(Message.Role.TOOL, List.of(new ContentBlock.Text("sunny")),
                                List.of(), "call_1", "getWeather")))
                .build();
        Map<String, Object> body = GeminiProtocol.buildRequestMap(req, null);
        List<?> contents = (List<?>) body.get("contents");
        assertEquals(3, contents.size());
        // assistant -> model + functionCall part
        Map<?, ?> assistant = (Map<?, ?>) contents.get(1);
        assertEquals("model", assistant.get("role"), "assistant 映射为 model");
        List<?> aParts = (List<?>) assistant.get("parts");
        Map<?, ?> fc = (Map<?, ?>) ((Map<?, ?>) aParts.get(0)).get("functionCall");
        assertEquals("getWeather", fc.get("name"));
        assertEquals("BJ", ((Map<?, ?>) fc.get("args")).get("city"));
        // 工具结果 -> user + functionResponse
        Map<?, ?> result = (Map<?, ?>) contents.get(2);
        assertEquals("user", result.get("role"));
        Map<?, ?> fr = (Map<?, ?>) ((Map<?, ?>) ((List<?>) result.get("parts")).get(0)).get("functionResponse");
        assertEquals("getWeather", fr.get("name"));
    }

    @Test
    void buildRequestToolResultErrorPayload() {
        ChatRequest req = ChatRequest.builder()
                .messages(List.of(
                        Message.of(Message.Role.USER, "weather?"),
                        Message.assistantWithCalls(
                                List.of(ToolCall.of("call_1", "getWeather", Map.of("city", "BJ"))), null),
                        new Message(Message.Role.TOOL, List.of(new ContentBlock.Text("boom")),
                                List.of(), "call_1", "getWeather", true)))
                .build();
        Map<String, Object> body = GeminiProtocol.buildRequestMap(req, null);
        Map<?, ?> result = (Map<?, ?>) ((List<?>) body.get("contents")).get(2);
        Map<?, ?> fr = (Map<?, ?>) ((Map<?, ?>) ((List<?>) result.get("parts")).get(0)).get("functionResponse");
        assertEquals("getWeather", fr.get("name"));
        Map<?, ?> response = (Map<?, ?>) fr.get("response");
        assertTrue(response.containsKey("error"), "执行失败 response 应传 error 而非 result");
        assertEquals("boom", response.get("error"));
        assertFalse(response.containsKey("result"));
    }

    @Test
    void buildRequestJsonModeSetsResponseMimeType() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "give me json"))
                .build();
        Map<String, Object> body = GeminiProtocol.buildRequestMap(req, Map.of("type", "json_object"));
        Map<?, ?> gen = (Map<?, ?>) body.get("generationConfig");
        assertNotNull(gen);
        assertEquals("application/json", gen.get("responseMimeType"), "JSON 模式用 responseMimeType");
    }

    @Test
    void parseResponseFunctionCall() {
        String json = """
                {"candidates":[{"content":{"parts":[
                    {"functionCall":{"name":"getWeather","args":{"city":"BJ"}}},
                    {"text":"done"}],"role":"model"},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5}}
                """;
        var resp = GeminiProtocol.parseResponse(json);
        assertEquals("done", resp.text());
        assertEquals(1, resp.toolCalls().size());
        ToolCall tc = resp.toolCalls().get(0);
        assertNull(tc.id(), "Gemini functionCall 不返回 id");
        assertEquals("getWeather", tc.name());
        assertEquals("BJ", tc.arguments().get("city"));
    }
}
