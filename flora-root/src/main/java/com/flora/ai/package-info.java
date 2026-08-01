/**
 * AI 统一接入包。
 * <p>提供统一的大模型 API 抽象：{@code com.flora.ai.api} 为统一接口接入口
 * （按能力拆分：对话/流式/JSON/多模态），{@code com.flora.ai.spi} 为 ServiceLoader
 * SPI 扩展机制（新厂商/新接口格式通过实现 {@code AiProvider} 接入），
 * {@code com.flora.ai.http} 为内置轻量 HTTP + SSE 流式基础设施（仅 JDK，零依赖）。</p>
 */
package com.flora.ai;
