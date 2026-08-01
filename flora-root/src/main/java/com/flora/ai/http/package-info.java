/**
 * AI 内置 HTTP 与 SSE 基础设施（仅 JDK，零外部依赖）。
 * <p>{@code HttpTransport} 封装 JDK {@code HttpClient} 的 JSON 请求与 SSE 流式响应，
 * {@code SseParser} 解析 {@code text/event-stream} 事件。供各 AI 提供者 SPI 实现复用。</p>
 */
package com.flora.ai.http;
