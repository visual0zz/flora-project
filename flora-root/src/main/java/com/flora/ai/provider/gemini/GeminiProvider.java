package com.flora.ai.provider.gemini;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ModelSpec;
import com.flora.ai.http.HttpTransport;
import com.flora.ai.spi.AiProvider;
import com.flora.ai.spi.Endpoint;

/**
 * Google Gemini 提供者 SPI：支持 provider 名为 {@code "gemini"} 的模型。
 */
public final class GeminiProvider implements AiProvider {

    @Override
    public boolean supports(ModelSpec model) {
        return "gemini".equals(model.provider());
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public ChatClient createClient(ModelSpec model, Endpoint endpoint) {
        return new GeminiClient(model, endpoint, HttpTransport.create());
    }
}
