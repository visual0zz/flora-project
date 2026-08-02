package com.flora.ai.api;

/**
 * 工具调用能力（可选）：请求可携带工具定义，响应可含模型发起的工具调用。
 * <p>支持工具调用（function calling）的客户端实现此接口；调用方用
 * {@code instanceof} 探测（与 {@link StreamingClient}/{@link JsonClient} 风格一致）。
 * 工具不引入新的 client 方法——它经由 {@link ChatRequest#tools()} 透传，
 * 由各厂商协议自行翻译或在不支持时抛异常。</p>
 */
public interface ToolClient {
}
