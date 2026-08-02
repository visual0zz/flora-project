package com.flora.ai.api.provider;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.Capability;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.GeminiOfficialChatClient;
import com.flora.ai.api.provider.client.GeminiOfficialJsonClient;
import com.flora.ai.api.provider.client.GeminiOfficialStreamClient;
import com.flora.ai.api.spi.AiProvider;

import java.util.EnumSet;
import java.util.Set;

/**
 * Gemini 官方提供者：绑定 {@link ApiKind#GEMINI_OFFICIAL}。
 */
public final class GeminiOfficialProvider implements AiProvider {

    @Override
    public ApiKind apiKind() {
        return ApiKind.GEMINI_OFFICIAL;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return EnumSet.of(Capability.CHAT, Capability.STREAM, Capability.JSON);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T createClient(Endpoint endpoint, Capability capability) {
        HttpTransport http = HttpTransport.create();
        return (T) switch (capability) {
            case CHAT -> new GeminiOfficialChatClient(endpoint, http);
            case STREAM -> new GeminiOfficialStreamClient(endpoint, http);
            case JSON -> new GeminiOfficialJsonClient(endpoint, http);
            default -> throw new IllegalArgumentException("Gemini 官方不支持能力: " + capability);
        };
    }
}
