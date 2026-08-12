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
 * OpenAI 官方提供者：绑定 {@link ApiSchema#OPENAI_OFFICIAL}。
 * <p>每能力创建一个 {@link OpenAiOfficialClient} 实例（多能力实现类）。</p>
 */
public final class OpenAiOfficialProvider implements AiProvider {

    @Override
    public ApiSchema apiSchema() {
        return ApiSchema.OPENAI_OFFICIAL;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public Set<IOMode> supportedCapabilities() {
        return EnumSet.of(IOMode.CHAT, IOMode.STREAM, IOMode.JSON);
    }

    @Override
    public ChatClient createClient(Endpoint endpoint) {
        // 实现类为多能力单类；按能力各 new 一个实例
        return new OpenAiOfficialClient(endpoint, HttpTransport.create());
    }
}
