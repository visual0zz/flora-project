package com.flora.ai.api.provider;

import com.flora.ai.api.impl.ApiKind;
import com.flora.ai.api.impl.ChatClient;
import com.flora.ai.api.impl.RegisteredModel;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.provider.client.GeminiClient;
import com.flora.ai.api.spi.AiProvider;

/**
 * Google Gemini 官方提供者：绑定 {@link ApiKind#GEMINI_OFFICIAL}。
 */
public final class GeminiProvider implements AiProvider {

    @Override
    public ApiKind apiKind() {
        return ApiKind.GEMINI_OFFICIAL;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public ChatClient createClient(RegisteredModel model) {
        return new GeminiClient(model, HttpTransport.create());
    }
}
