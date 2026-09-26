package com.flora.root.ai.api.provider.protocol;

import com.flora.root.ai.api.ChatResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link OpenAiProtocol} 用量解析回归测试：修复 {@code prompt_tokens_details} 被整体传给
 * {@code intOf} 导致 {@code cacheReadTokens} 恒为 0 的问题。
 */
class OpenAiProtocolUsageTest {

    @Test
    void parsesCachedTokensFromPromptTokensDetails() {
        String json = """
                {"choices":[{"message":{"content":"hi"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5,
                          "prompt_tokens_details":{"cached_tokens":7,"audio_tokens":1}}}
                """;
        ChatResponse resp = OpenAiProtocol.parseResponse(json);
        assertEquals(7, resp.usage().cacheReadTokens(),
                "cacheReadTokens 应取自 prompt_tokens_details.cached_tokens");
    }

    @Test
    void zeroWhenNoDetails() {
        String json = """
                {"choices":[{"message":{"content":"hi"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":5}}
                """;
        ChatResponse resp = OpenAiProtocol.parseResponse(json);
        assertEquals(0, resp.usage().cacheReadTokens());
    }
}
