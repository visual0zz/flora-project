package com.flora.ai.spi;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ModelSpec;

/**
 * AI 提供者 SPI：声明支持的模型并创建客户端。
 * <p>实现类由 ServiceLoader 加载（消费方提供 {@code META-INF/services/com.flora.ai.spi.AiProvider}）。
 * 每个实现负责一个厂商协议（OpenAI / Anthropic / Gemini ...），把统一 {@code ChatRequest}
 * 翻译为各家协议；返回的 {@link ChatClient} 可按需实现流式/JSON/多模态能力接口。</p>
 *
 * <pre>{@code
 * public final class OpenAiProvider implements AiProvider {
 *     public boolean supports(ModelSpec m) { return m.provider().equals("openai"); }
 *     public ChatClient createClient(ModelSpec m, Endpoint e) { return new OpenAiClient(m, e); }
 * }
 * }</pre>
 */
public interface AiProvider {

    /** 是否支持指定模型（按 id 或前缀/厂商匹配）。 */
    boolean supports(ModelSpec model);

    /** 提供者标识（如 "openai"、"anthropic"）。 */
    String name();

    /** 为指定模型创建客户端（实现类按需实现 StreamingClient/JsonClient/MultimodalClient）。 */
    ChatClient createClient(ModelSpec model, Endpoint endpoint);
}
