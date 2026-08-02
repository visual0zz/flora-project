package com.flora.ai.api.provider;

import com.flora.ai.api.ApiKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 五家 Provider 的 apiKind/name 断言。
 */
class ProviderTest {

    @Test
    void openAiProvider() {
        OpenAiOfficialProvider p = new OpenAiOfficialProvider();
        assertEquals("openai", p.name());
        assertEquals(ApiKind.OPENAI_OFFICIAL, p.apiKind());
    }

    @Test
    void openAiLikeProvider() {
        OpenAiLikeProvider p = new OpenAiLikeProvider();
        assertEquals("openai-like", p.name());
        assertEquals(ApiKind.OPENAI_LIKE, p.apiKind());
    }

    @Test
    void anthropicProvider() {
        AnthropicOfficialProvider p = new AnthropicOfficialProvider();
        assertEquals("anthropic", p.name());
        assertEquals(ApiKind.ANTHROPIC_OFFICIAL, p.apiKind());
    }

    @Test
    void geminiProvider() {
        GeminiOfficialProvider p = new GeminiOfficialProvider();
        assertEquals("gemini", p.name());
        assertEquals(ApiKind.GEMINI_OFFICIAL, p.apiKind());
    }

    @Test
    void deepSeekProvider() {
        DeepSeekOfficialProvider p = new DeepSeekOfficialProvider();
        assertEquals("deepseek", p.name());
        assertEquals(ApiKind.DEEPSEEK_OFFICIAL, p.apiKind());
    }
}
