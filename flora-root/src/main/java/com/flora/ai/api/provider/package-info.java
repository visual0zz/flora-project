/**
 * AI 厂商适配器。
 * <p>各厂商的 {@code AiProvider} 实现，把统一 {@code ChatRequest} 翻译为各家协议并解析响应。
 * 内置五类：OpenAI 官方（Chat Completions）、OpenAI 兼容接口（第三方兼容端点）、
 * Anthropic 官方（Messages）、Gemini 官方（generateContent）、DeepSeek 官方
 * （OpenAI 兼容格式）。各协议翻译在 {@code protocol} 子包，客户端在 {@code client} 子包。</p>
 */
package com.flora.ai.api.provider;
