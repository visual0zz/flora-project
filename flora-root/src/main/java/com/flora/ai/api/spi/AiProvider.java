package com.flora.ai.api.spi;

import com.flora.ai.api.ApiKind;
import com.flora.ai.api.Capability;
import com.flora.ai.api.Endpoint;

import java.util.Set;

/**
 * AI 提供者 SPI：绑定一种 {@link ApiKind} 协议，声明支持的能力并按能力创建单能力 client。
 * <p>内置 provider（OpenAI/Anthropic/Gemini 等）由 {@code AiApi} 直接代码注册；
 * 外部新厂商实现本接口并通过 ServiceLoader（{@code META-INF/services/com.flora.ai.api.spi.AiProvider}）
 * 注册。注册端点时，端点声明的 {@code capabilities} 必须 ⊆ 本 provider 的
 * {@link #supportedCapabilities()}，否则注册报错。</p>
 *
 * <pre>{@code
 * public final class MyProvider implements AiProvider {
 *     public ApiKind apiKind() { return ApiKind.OPENAI_LIKE; }
 *     public String name() { return "my"; }
 *     public Set<Capability> supportedCapabilities() {
 *         return EnumSet.of(Capability.CHAT, Capability.STREAM, Capability.JSON);
 *     }
 *     public <T> T createClient(Endpoint endpoint, Capability capability) {
 *         return (T) switch (capability) {
 *             case CHAT -> new MyChatClient(endpoint);
 *             case STREAM -> new MyStreamClient(endpoint);
 *             case JSON -> new MyJsonClient(endpoint);
 *             default -> throw new IllegalArgumentException("不支持能力: " + capability);
 *         };
 *     }
 * }
 * }</pre>
 */
public interface AiProvider {

    /** 本 provider 支持的模型 API 类型。 */
    ApiKind apiKind();

    /** 提供者标识（如 "openai"、"anthropic"）。 */
    String name();

    /** 本 provider 协议支持的能力集合（决定哪些 client 可被构造）。 */
    Set<Capability> supportedCapabilities();

    /** 按注册端点 + 能力创建单能力 client；不支持的能力抛异常。 */
    <T> T createClient(Endpoint endpoint, Capability capability);
}
