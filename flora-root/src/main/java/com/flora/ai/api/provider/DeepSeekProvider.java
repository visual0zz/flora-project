package com.flora.ai.api.provider;

import com.flora.ai.api.impl.ApiKind;
import com.flora.ai.api.impl.ChatClient;
import com.flora.ai.api.impl.RegisteredModel;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.OpenAiClient;
import com.flora.ai.api.spi.AiProvider;

/**
 * DeepSeek 官方提供者：绑定 {@link ApiKind#DEEPSEEK_OFFICIAL}。
 * <p>DeepSeek API 为 OpenAI 兼容格式，复用 OpenAI 协议实现（独立 provider 类）。</p>
 */
public final class DeepSeekProvider implements AiProvider {

    @Override
    public ApiKind apiKind() {
        return ApiKind.DEEPSEEK_OFFICIAL;
    }

    @Override
    public String name() {
        return "deepseek";
    }

    @Override
    public ChatClient createClient(RegisteredModel model) {
        return new OpenAiClient(model, HttpTransport.create());
    }
}
