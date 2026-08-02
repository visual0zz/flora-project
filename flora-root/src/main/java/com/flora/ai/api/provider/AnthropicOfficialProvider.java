package com.flora.ai.api.provider;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.AnthropicOfficialClient;
import com.flora.ai.api.spi.AiProvider;

/**
 * Anthropic 官方提供者：绑定 {@link ApiKind#ANTHROPIC_OFFICIAL}。
 */
public final class AnthropicOfficialProvider implements AiProvider {

    @Override
    public ApiKind apiKind() {
        return ApiKind.ANTHROPIC_OFFICIAL;
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public ChatClient createClient(Endpoint endpoint) {
        return new AnthropicOfficialClient(endpoint, HttpTransport.create());
    }
}
