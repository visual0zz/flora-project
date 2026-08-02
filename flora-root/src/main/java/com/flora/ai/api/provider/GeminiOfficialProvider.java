package com.flora.ai.api.provider;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.GeminiOfficialClient;
import com.flora.ai.api.spi.AiProvider;

/**
 * Google Gemini 官方提供者：绑定 {@link ApiKind#GEMINI_OFFICIAL}。
 */
public final class GeminiOfficialProvider implements AiProvider {

    @Override
    public ApiKind apiKind() {
        return ApiKind.GEMINI_OFFICIAL;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public ChatClient createClient(Endpoint endpoint) {
        return new GeminiOfficialClient(endpoint, HttpTransport.create());
    }
}
