package com.flora.ai.api.provider;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.Capability;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.OpenAiOfficialChatClient;
import com.flora.ai.api.provider.client.OpenAiOfficialJsonClient;
import com.flora.ai.api.provider.client.OpenAiOfficialStreamClient;
import com.flora.ai.api.spi.AiProvider;

import java.util.EnumSet;
import java.util.Set;

/**
 * OpenAI 官方提供者：绑定 {@link ApiKind#OPENAI_OFFICIAL}。
 */
public final class OpenAiOfficialProvider implements AiProvider {

    @Override
    public ApiKind apiKind() {
        return ApiKind.OPENAI_OFFICIAL;
    }

    @Override
    public String name() {
        return "openai";
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
            case CHAT -> new OpenAiOfficialChatClient(endpoint, http);
            case STREAM -> new OpenAiOfficialStreamClient(endpoint, http);
            case JSON -> new OpenAiOfficialJsonClient(endpoint, http);
            default -> throw new IllegalArgumentException("OpenAI 官方不支持能力: " + capability);
        };
    }
}
