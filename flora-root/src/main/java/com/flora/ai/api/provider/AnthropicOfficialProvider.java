package com.flora.ai.api.provider;

import com.flora.ai.api.*;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.AnthropicOfficialClient;
import com.flora.ai.api.spi.AiProvider;

import java.util.EnumSet;
import java.util.Set;

/**
 * Anthropic 官方提供者：绑定 {@link ApiSchema#ANTHROPIC_OFFICIAL}。
 */
public final class AnthropicOfficialProvider implements AiProvider {

    @Override
    public ApiSchema apiSchema() {
        return ApiSchema.ANTHROPIC_OFFICIAL;
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public Set<IOMode> supportedCapabilities() {
        return EnumSet.of(IOMode.CHAT, IOMode.STREAM, IOMode.JSON);
    }

    @Override
    public ChatClient createClient(Endpoint endpoint) {
        // 实现类为多能力单类；按能力各 new 一个实例
        return new AnthropicOfficialClient(endpoint, HttpTransport.create());
    }
}
