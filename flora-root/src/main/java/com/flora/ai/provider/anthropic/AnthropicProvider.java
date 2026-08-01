package com.flora.ai.provider.anthropic;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ModelSpec;
import com.flora.ai.http.HttpTransport;
import com.flora.ai.spi.AiProvider;
import com.flora.ai.spi.Endpoint;

/**
 * Anthropic 提供者 SPI：支持 provider 名为 {@code "anthropic"} 的模型。
 */
public final class AnthropicProvider implements AiProvider {

    @Override
    public boolean supports(ModelSpec model) {
        return "anthropic".equals(model.provider());
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public ChatClient createClient(ModelSpec model, Endpoint endpoint) {
        return new AnthropicClient(model, endpoint, HttpTransport.create());
    }
}
