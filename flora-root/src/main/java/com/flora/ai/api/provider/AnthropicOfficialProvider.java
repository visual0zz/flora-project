package com.flora.ai.api.provider;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.Capability;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.AnthropicOfficialClient;
import com.flora.ai.api.spi.AiProvider;

import java.util.EnumSet;
import java.util.Set;

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
    public Set<Capability> supportedCapabilities() {
        return EnumSet.of(Capability.CHAT, Capability.STREAM);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T createClient(Endpoint endpoint, Capability capability) {
        // 实现类为多能力单类；按能力各 new 一个实例
        return (T) new AnthropicOfficialClient(endpoint, HttpTransport.create());
    }
}
