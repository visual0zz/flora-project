package com.flora.ai.api.provider;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.OpenAiLikeClient;
import com.flora.ai.api.spi.AiProvider;

/**
 * OpenAI 风格兼容接口提供者：绑定 {@link ApiKind#OPENAI_LIKE}。
 * <p>复用 OpenAI Chat Completions 协议实现，面向第三方 OpenAI 兼容端点
 * （Together/Fireworks/vLLM/Ollama 等）。</p>
 */
public final class OpenAiLikeProvider implements AiProvider {

    @Override
    public ApiKind apiKind() {
        return ApiKind.OPENAI_LIKE;
    }

    @Override
    public String name() {
        return "openai-like";
    }

    @Override
    public ChatClient createClient(Endpoint endpoint) {
        return new OpenAiLikeClient(endpoint, HttpTransport.create());
    }
}
