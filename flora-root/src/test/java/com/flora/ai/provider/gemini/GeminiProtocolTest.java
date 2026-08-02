package com.flora.ai.provider.gemini;

import com.flora.ai.api.impl.ChatRequest;
import com.flora.ai.api.impl.ContentBlock;
import com.flora.ai.api.impl.Message;
import com.flora.ai.api.provider.protocal.GeminiProtocol;
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
                .messages(List.of(
                        Message.of(Message.Role.SYSTEM, "you are gemini"),
                        Message.of(Message.Role.USER, "hello"),
                        Message.of(Message.Role.ASSISTANT, "hi there")))
                .build();
        Map<String, Object> body = GeminiProtocol.buildRequestMap(req);
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
                new ContentBlock.Image("data:image/png;base64,xyz", "image/png")));
        ChatRequest req = ChatRequest.builder().message(m).build();
        Map<String, Object> body = GeminiProtocol.buildRequestMap(req);
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
}
