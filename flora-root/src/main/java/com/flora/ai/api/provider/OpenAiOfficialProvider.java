package com.flora.ai.api.provider;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.Capability;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.OpenAiOfficialClient;
import com.flora.ai.api.spi.AiProvider;

import java.util.EnumSet;
import java.util.Set;

/**
 * OpenAI 官方提供者：绑定 {@link ApiKind#OPENAI_OFFICIAL}。
 * <p>每能力创建一个 {@link OpenAiOfficialClient} 实例（多能力实现类）。</p>
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
        // 实现类为多能力单类；按能力各 new 一个实例
        return (T) new OpenAiOfficialClient(endpoint, HttpTransport.create());
    }
}
