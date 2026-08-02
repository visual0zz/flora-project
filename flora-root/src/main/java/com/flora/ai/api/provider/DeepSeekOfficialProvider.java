package com.flora.ai.api.provider;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.Capability;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.client.DeepSeekOfficialChatClient;
import com.flora.ai.api.provider.client.DeepSeekOfficialJsonClient;
import com.flora.ai.api.provider.client.DeepSeekOfficialStreamClient;
import com.flora.ai.api.spi.AiProvider;

import java.util.EnumSet;
import java.util.Set;

/**
 * DeepSeek 官方提供者：绑定 {@link ApiKind#DEEPSEEK_OFFICIAL}。
 * <p>使用独立 {@code DeepSeekProtocol}（JSON 仅 json_object、reasoner 拒绝工具调用）。</p>
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
    public Set<Capability> supportedCapabilities() {
        return EnumSet.of(Capability.CHAT, Capability.STREAM, Capability.JSON);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T createClient(Endpoint endpoint, Capability capability) {
        HttpTransport http = HttpTransport.create();
        return (T) switch (capability) {
            case CHAT -> new DeepSeekOfficialChatClient(endpoint, http);
            case STREAM -> new DeepSeekOfficialStreamClient(endpoint, http);
            case JSON -> new DeepSeekOfficialJsonClient(endpoint, http);
            default -> throw new IllegalArgumentException("DeepSeek 官方不支持能力: " + capability);
        };
    }
}
