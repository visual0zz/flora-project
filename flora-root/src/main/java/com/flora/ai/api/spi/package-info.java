/**
 * AI 提供者 SPI 扩展机制与路由。
 * <p>新厂商/新接口格式通过实现 {@link AiProvider} 接入，内置 provider 由
 * {@code AiApi} 直接代码注册，外部厂商可经 ServiceLoader 附加加载（消费方在
 * {@code META-INF/services/com.flora.ai.api.spi.AiProvider} 注册实现类）。
 * 实现类负责把统一 {@code ChatRequest} 翻译为各家协议，并可选实现流式/JSON/多模态能力。
 * {@link Router} 负责从注册端点中选择本次任务使用的模型。</p>
 */
package com.flora.ai.api.spi;
