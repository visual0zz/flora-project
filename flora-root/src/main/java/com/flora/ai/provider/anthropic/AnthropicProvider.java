package com.flora.ai.provider.anthropic;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.RegisteredModel;
import com.flora.ai.http.HttpTransport;
import com.flora.ai.spi.AiProvider;

/**
 * Anthropic 官方提供者：绑定 {@link ApiKind#ANTHROPIC_OFFICIAL}。
 */
public final class AnthropicProvider implements AiProvider {

    @Override
    public ApiKind apiKind() {
        return ApiKind.ANTHROPIC_OFFICIAL;
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public ChatClient createClient(RegisteredModel model) {
        return new AnthropicClient(model, HttpTransport.create());
    }
}
