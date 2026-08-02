package com.flora.ai.provider;

import com.flora.ai.api.impl.ApiKind;
import com.flora.ai.api.provider.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 五家 Provider 的 apiKind/name 断言。
 */
class ProviderTest {

    @Test
    void openAiProvider() {
        OpenAiProvider p = new OpenAiProvider();
        assertEquals("openai", p.name());
        assertEquals(ApiKind.OPENAI_OFFICIAL, p.apiKind());
    }

    @Test
    void openAiCompatibleProvider() {
        OpenAiCompatibleProvider p = new OpenAiCompatibleProvider();
        assertEquals("openai-compatible", p.name());
        assertEquals(ApiKind.OPENAI_COMPATIBLE, p.apiKind());
    }

    @Test
    void anthropicProvider() {
        AnthropicProvider p = new AnthropicProvider();
        assertEquals("anthropic", p.name());
        assertEquals(ApiKind.ANTHROPIC_OFFICIAL, p.apiKind());
    }

    @Test
    void geminiProvider() {
        GeminiProvider p = new GeminiProvider();
        assertEquals("gemini", p.name());
        assertEquals(ApiKind.GEMINI_OFFICIAL, p.apiKind());
    }

    @Test
    void deepSeekProvider() {
        DeepSeekProvider p = new DeepSeekProvider();
        assertEquals("deepseek", p.name());
        assertEquals(ApiKind.DEEPSEEK_OFFICIAL, p.apiKind());
    }
}
