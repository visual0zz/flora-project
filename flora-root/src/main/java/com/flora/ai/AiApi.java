package com.flora.ai;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ModelSpec;
import com.flora.ai.spi.AiProvider;
import com.flora.ai.spi.Endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * AI 统一接入门面。
 * <p>通过 ServiceLoader 收集 {@link AiProvider} 实现，按模型规格匹配并创建客户端。
 * 新厂商无需改本类——实现 {@code AiProvider} 并在 classpath 注册 services 文件即可。</p>
 *
 * <pre>{@code
 * ChatClient c = AiApi.chat(ModelSpec.of("gpt-5", "openai"), Endpoint.of(url, key));
 * String answer = c.ask(ChatRequest.builder().model(spec).message(...).build());
 * }</pre>
 */
public final class AiApi {

    private static final List<AiProvider> PROVIDERS = loadProviders();

    private AiApi() {
    }

    /** 按模型规格获取客户端；无 provider 支持时抛异常。 */
    public static ChatClient chat(ModelSpec model, Endpoint endpoint) {
        if (model == null) {
            throw new IllegalArgumentException("model 不能为空");
        }
        for (AiProvider provider : PROVIDERS) {
            if (provider.supports(model)) {
                return provider.createClient(model, endpoint);
            }
        }
        throw new IllegalArgumentException("没有 provider 支持模型: " + model.id()
                + "（已注册: " + providers().stream().map(AiProvider::name).toList() + "）");
    }

    /** 列出所有已注册的 provider。 */
    public static List<AiProvider> providers() {
        return List.copyOf(PROVIDERS);
    }

    private static List<AiProvider> loadProviders() {
        List<AiProvider> list = new ArrayList<>();
        for (AiProvider provider : ServiceLoader.load(AiProvider.class)) {
            list.add(provider);
        }
        return List.copyOf(list);
    }
}
