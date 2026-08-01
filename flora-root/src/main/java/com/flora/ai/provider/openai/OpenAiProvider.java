package com.flora.ai.provider.openai;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ModelSpec;
import com.flora.ai.http.HttpTransport;
import com.flora.ai.provider.QueueStreamIterator;
import com.flora.ai.spi.AiProvider;
import com.flora.ai.spi.Endpoint;

/**
 * OpenAI 提供者 SPI：支持 provider 名为 {@code "openai"} 的模型。
 */
public final class OpenAiProvider implements AiProvider {

    @Override
    public boolean supports(ModelSpec model) {
        return "openai".equals(model.provider());
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public ChatClient createClient(ModelSpec model, Endpoint endpoint) {
        return new OpenAiClient(model, endpoint, HttpTransport.create());
    }
}
