package com.flora.ai.provider.openai;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ContentBlock;
import com.flora.ai.api.Message;
import com.flora.ai.api.SamplingConfig;
import com.flora.ai.api.ThinkingConfig;
import com.flora.ai.api.TokenUsage;
import com.flora.codec.json.JsonParser;
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
                .messages(List.of(
                        Message.of(Message.Role.SYSTEM, "be nice"),
                        Message.of(Message.Role.USER, "hello")))
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
    void buildRequestSampling() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .sampling(SamplingConfig.of(0.5, 128))
                .build();
        Map<String, Object> body = OpenAiProtocol.buildRequestMap(req, MODEL, false);
        assertEquals(0.5, body.get("temperature"));
        assertEquals(128, body.get("max_tokens"));
    }

    @Test
    void buildRequestThinking() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "hi"))
                .thinking(ThinkingConfig.of(ThinkingConfig.Mode.ON, ThinkingConfig.Effort.HIGH))
                .build();
        Map<String, Object> body = OpenAiProtocol.buildRequestMap(req, MODEL, false);
        assertEquals("high", body.get("reasoning_effort"));
    }

    @Test
    void buildRequestMultimodal() {
        Message m = new Message(Message.Role.USER, List.of(
                new ContentBlock.Text("what is this?"),
                new ContentBlock.Image("data:image/png;base64,abc", "image/png")));
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
        Map<String, Object> root = JsonParser.parseObject(chunk);
        List<?> choices = (List<?>) root.get("choices");
        Map<?, ?> delta = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("delta");
        assertEquals("Hello", delta.get("content"));
        assertEquals("hmm", delta.get("reasoning_content"));
    }
}
