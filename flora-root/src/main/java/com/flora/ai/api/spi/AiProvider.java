package com.flora.ai.api.spi;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.Endpoint;

/**
 * AI 提供者 SPI：绑定一种 {@link ApiKind} 协议，按注册端点创建客户端。
 * <p>内置 provider（OpenAI/Anthropic/Gemini 等）由 {@code AiApi} 直接代码注册；
 * 外部新厂商实现本接口并通过 ServiceLoader（{@code META-INF/services/com.flora.ai.api.spi.AiProvider}）
 * 注册。每个实现负责一种 API 类型的协议翻译（如 OpenAI 官方、Anthropic 官方）。</p>
 *
 * <pre>{@code
 * public final class MyProvider implements AiProvider {
 *     public ApiKind apiKind() { return ApiKind.OPENAI_COMPATIBLE; }
 *     public ChatClient createClient(Endpoint model) { return new MyClient(model); }
 * }
 * }</pre>
 */
public interface AiProvider {

    /** 本 provider 支持的模型 API 类型。 */
    ApiKind apiKind();

    /** 提供者标识（如 "openai"、"anthropic"）。 */
    String name();

    /** 按注册端点创建客户端（实现类按需实现 StreamingClient/JsonClient/MultimodalClient）。 */
    ChatClient createClient(Endpoint model);
}
