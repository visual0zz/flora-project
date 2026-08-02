/**
 * AI 提供者 SPI 扩展机制。
 * <p>新厂商/新接口格式通过实现 {@link AiProvider} 接入，由 ServiceLoader 加载
 * （消费方在 {@code META-INF/services/com.flora.ai.spi.AiProvider} 注册实现类）。
 * 实现类负责把统一 {@code ChatRequest} 翻译为各家协议，并可选实现流式/JSON/多模态能力。</p>
 */
package com.flora.ai.api.spi;
