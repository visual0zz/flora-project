package com.flora.root.ai.api.provider;

import com.flora.root.ai.api.*;
import com.flora.root.ai.api.ApiSchema;
import com.flora.root.ai.api.ChatClient;
import com.flora.root.ai.api.Endpoint;
import com.flora.root.ai.api.IOMode;
import com.flora.root.ai.api.impl.HttpTransport;
import com.flora.root.ai.api.provider.client.OpenAiOfficialClient;
import com.flora.root.ai.api.spi.AiProvider;

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
    public Set<IOMode> supportedCapabilities() {
        return EnumSet.of(IOMode.CHAT, IOMode.STREAM, IOMode.JSON);
    }

    @Override
    public ChatClient createClient(Endpoint endpoint) {
        return new OpenAiOfficialClient(endpoint, HttpTransport.create());
    }
}
