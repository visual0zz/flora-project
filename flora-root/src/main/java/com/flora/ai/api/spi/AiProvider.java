package com.flora.ai.api.spi;

import com.flora.ai.api.*;

import java.util.Set;

/**
 * AI 提供者 SPI：绑定一种 {@link ApiSchema} 协议，声明支持的能力并按注册端点创建 client。
 * <p>内置 provider（OpenAI/Anthropic/Gemini 等）由 {@code AiApi} 直接代码注册；
 * 外部新厂商实现本接口并通过 ServiceLoader（{@code META-INF/services/com.flora.ai.api.spi.AiProvider}）
 * 注册。注册端点时，端点的 {@code capability} 必须 ∈ {@link #supportedCapabilities()}，
 * 否则注册报错。每个 Endpoint 对应一个 client 实例（一对一）。</p>
 *
 * <pre>{@code
 * public final class MyProvider implements AiProvider {
 *     public ApiSchema apiSchema() { return ApiSchema.OPENAI_LIKE; }
 *     public String name() { return "my"; }
 *     public Set<Capability> supportedCapabilities() {
 *         return EnumSet.of(Capability.CHAT, Capability.STREAM);
 *     }
 *     public ChatClient createClient(Endpoint endpoint) {
 *         return new MyClient(endpoint);
 *     }
 * }
 * }</pre>
 */
public interface AiProvider {

    /** 本 provider 支持的模型 API 类型。 */
    ApiSchema apiSchema();

    /** 提供者标识（如 "openai"、"anthropic"）。 */
    String name();

    /** 本 provider 协议支持的能力集合（校验用）。 */
    Set<Capability> supportedCapabilities();

    /** 按注册端点创建 client（端点已含能力）。 */
    ChatClient createClient(Endpoint endpoint);
}
