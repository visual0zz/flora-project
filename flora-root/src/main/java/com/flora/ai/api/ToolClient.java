package com.flora.ai.api;

/**
 * 工具调用能力（可选）：请求可携带工具定义，响应可含模型发起的工具调用。
 * <p>支持工具调用（function calling）的客户端实现此接口。它不作为独立能力注册
 * （{@link Capability} 无 TOOL 项），而是经由已有 client（{@link ChatClient} 等）
 * 用 {@code instanceof} 探测其是否支持工具——探测结果决定调用方是否应在
 * {@link ChatRequest#tools()} 中携带工具定义。</p>
 */
public interface ToolClient {
}
