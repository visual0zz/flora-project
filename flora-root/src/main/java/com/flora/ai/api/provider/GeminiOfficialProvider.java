package com.flora.ai.api.provider;

import com.flora.ai.api.*;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.GeminiOfficialClient;
import com.flora.ai.api.spi.AiProvider;

import java.util.EnumSet;
import java.util.Set;

/**
 * Gemini 官方提供者：绑定 {@link ApiSchema#GEMINI_OFFICIAL}。
 */
public final class GeminiOfficialProvider implements AiProvider {

    @Override
    public ApiSchema apiKind() {
        return ApiSchema.GEMINI_OFFICIAL;
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
    public ChatClient createClient(Endpoint endpoint) {
        // 实现类为多能力单类；按能力各 new 一个实例
        return new GeminiOfficialClient(endpoint, HttpTransport.create());
    }
}
