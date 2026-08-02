package com.flora.ai.api.provider;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.DeepSeekOfficialClient;
import com.flora.ai.api.spi.AiProvider;

/**
 * DeepSeek 官方提供者：绑定 {@link ApiKind#DEEPSEEK_OFFICIAL}。
 * <p>使用独立 {@code DeepSeekProtocol}（JSON 仅 json_object、reasoner 拒绝工具调用），
 * 客户端为 {@code DeepSeekOfficialClient}。</p>
 */
public final class DeepSeekOfficialProvider implements AiProvider {

    @Override
    public ApiKind apiKind() {
        return ApiKind.DEEPSEEK_OFFICIAL;
    }

    @Override
    public String name() {
        return "deepseek";
    }

    @Override
    public ChatClient createClient(Endpoint endpoint) {
        return new DeepSeekOfficialClient(endpoint, HttpTransport.create());
    }
}
