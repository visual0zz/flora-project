package com.flora.ai.api.provider;

import com.flora.ai.api.ApiSchema;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.Capability;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.OpenAiOfficialClient;
import com.flora.ai.api.spi.AiProvider;

import java.util.EnumSet;
import java.util.Set;

/**
 * OpenAI 风格兼容端点提供者：绑定 {@link ApiSchema#OPENAI_LIKE}。
 * <p>复用 {@link OpenAiOfficialClient}（协议相同），面向第三方 OpenAI 兼容端点
 * （Together/Fireworks/vLLM/Ollama 等）。</p>
 */
public final class OpenAiLikeProvider implements AiProvider {

    @Override
    public ApiSchema apiSchema() {
        return ApiSchema.OPENAI_LIKE;
    }

    @Override
    public String name() {
        return "openai-like";
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return EnumSet.of(Capability.CHAT, Capability.STREAM, Capability.JSON);
    }

    @Override
    public ChatClient createClient(Endpoint endpoint) {
        return new OpenAiOfficialClient(endpoint, HttpTransport.create());
    }
}
