package com.flora.ai.provider;

import com.flora.ai.provider.anthropic.AnthropicProvider;
import com.flora.ai.provider.gemini.GeminiProvider;
import com.flora.ai.provider.openai.OpenAiProvider;
import org.junit.jupiter.api.Test;

import com.flora.ai.api.ModelSpec;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 三个厂商 Provider 的 supports/name 断言。
 */
class ProviderTest {

    @Test
    void openAiProvider() {
        OpenAiProvider p = new OpenAiProvider();
        assertEquals("openai", p.name());
        assertTrue(p.supports(ModelSpec.of("gpt-5", "openai")));
        assertFalse(p.supports(ModelSpec.of("claude", "anthropic")));
    }

    @Test
    void anthropicProvider() {
        AnthropicProvider p = new AnthropicProvider();
        assertEquals("anthropic", p.name());
        assertTrue(p.supports(ModelSpec.of("claude-sonnet-4", "anthropic")));
        assertFalse(p.supports(ModelSpec.of("gemini", "gemini")));
    }

    @Test
    void geminiProvider() {
        GeminiProvider p = new GeminiProvider();
        assertEquals("gemini", p.name());
        assertTrue(p.supports(ModelSpec.of("gemini-2.5-flash", "gemini")));
        assertFalse(p.supports(ModelSpec.of("gpt", "openai")));
    }
}
