package com.flora.ai.api.provider;

import com.flora.ai.api.ApiSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 五家 Provider 的 apiSchema/name 断言。
 */
class ProviderTest {

    @Test
    void openAiProvider() {
        OpenAiOfficialProvider p = new OpenAiOfficialProvider();
        assertEquals("openai", p.name());
        assertEquals(ApiSchema.OPENAI_OFFICIAL, p.apiSchema());
    }

    @Test
    void openAiLikeProvider() {
        OpenAiLikeProvider p = new OpenAiLikeProvider();
        assertEquals("openai-like", p.name());
        assertEquals(ApiSchema.OPENAI_LIKE, p.apiSchema());
    }

    @Test
    void anthropicProvider() {
        AnthropicOfficialProvider p = new AnthropicOfficialProvider();
        assertEquals("anthropic", p.name());
        assertEquals(ApiSchema.ANTHROPIC_OFFICIAL, p.apiSchema());
    }

    @Test
    void geminiProvider() {
        GeminiOfficialProvider p = new GeminiOfficialProvider();
        assertEquals("gemini", p.name());
        assertEquals(ApiSchema.GEMINI_OFFICIAL, p.apiSchema());
    }

    @Test
    void deepSeekProvider() {
        DeepSeekOfficialProvider p = new DeepSeekOfficialProvider();
        assertEquals("deepseek", p.name());
        assertEquals(ApiSchema.DEEPSEEK_OFFICIAL, p.apiSchema());
    }
}
