package com.flora.ai.provider.gemini;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.RegisteredModel;
import com.flora.ai.http.HttpTransport;
import com.flora.ai.spi.AiProvider;

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
