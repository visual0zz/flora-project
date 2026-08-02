/**
 * AI 厂商适配器。
 * <p>各厂商的 {@code AiProvider} SPI 实现，把统一 {@code ChatRequest} 翻译为各家协议，
 * 并解析响应。当前预制 OpenAI（Chat Completions）、Anthropic（Messages）、
 * Gemini（generateContent）。</p>
 */
package com.flora.ai.api.provider;
