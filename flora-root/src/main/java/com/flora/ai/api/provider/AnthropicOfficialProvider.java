package com.flora.ai.api.provider;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.Capability;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.AnthropicOfficialChatClient;
import com.flora.ai.api.provider.client.AnthropicOfficialStreamClient;
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
        HttpTransport http = HttpTransport.create();
        return (T) switch (capability) {
            case CHAT -> new AnthropicOfficialChatClient(endpoint, http);
            case STREAM -> new AnthropicOfficialStreamClient(endpoint, http);
            default -> throw new IllegalArgumentException("Anthropic 官方不支持能力: " + capability);
        };
    }
}
