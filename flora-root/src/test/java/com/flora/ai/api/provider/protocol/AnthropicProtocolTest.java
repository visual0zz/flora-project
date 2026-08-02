package com.flora.ai.api.provider.protocol;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Message;
import com.flora.ai.api.ThinkingConfig;
import com.flora.ai.api.provider.protocol.AnthropicProtocol;
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
                .messages(List.of(
                        Message.of(Message.Role.SYSTEM, "you are claude"),
                        Message.of(Message.Role.USER, "hi")))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false);
        assertEquals("you are claude", body.get("system"));
        List<?> messages = (List<?>) body.get("messages");
        assertEquals(1, messages.size(), "SYSTEM 消息不应出现在 messages");
        assertEquals("user", ((Map<?, ?>) messages.get(0)).get("role"));
    }

    @Test
    void buildRequestDefaultMaxTokens() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false);
        assertEquals(AnthropicProtocol.DEFAULT_MAX_TOKENS, body.get("max_tokens"));
    }

    @Test
    void buildRequestThinking() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .thinking(ThinkingConfig.of(ThinkingConfig.Mode.ON, ThinkingConfig.Effort.HIGH))
                .build();
        Map<String, Object> body = AnthropicProtocol.buildRequestMap(req, MODEL, false);
        Map<?, ?> thinking = (Map<?, ?>) body.get("thinking");
        assertNotNull(thinking);
        assertEquals("enabled", thinking.get("type"));
        assertNotNull(thinking.get("budget_tokens"));
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
}
