package com.flora.ai.api.provider;

import com.flora.ai.api.impl.ApiKind;
import com.flora.ai.api.impl.ChatClient;
import com.flora.ai.api.impl.RegisteredModel;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.OpenAiClient;
import com.flora.ai.api.spi.AiProvider;

/**
 * OpenAI 官方提供者 SPI：绑定 {@link ApiKind#OPENAI_OFFICIAL}。
 */
public final class OpenAiProvider implements AiProvider {

    @Override
    public ApiKind apiKind() {
        return ApiKind.OPENAI_OFFICIAL;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public ChatClient createClient(RegisteredModel model) {
        return new OpenAiClient(model, HttpTransport.create());
    }
}
