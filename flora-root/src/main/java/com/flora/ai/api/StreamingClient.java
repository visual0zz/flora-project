package com.flora.ai.api;

/**
 * 流式输出能力（可选）。
 * <p>支持流式生成（SSE）的客户端实现此接口。调用方：
 * <pre>{@code
 * if (client instanceof StreamingClient sc) {
 *     try (StreamIterator it = sc.stream(req)) {
 *         String text = it.collectText();
 *     }
 * }
 * }</pre></p>
 */
public interface StreamingClient extends ApiClient {

    /** 流式发送请求，返回事件迭代器（用后应关闭）。 */
    StreamIterator stream(ChatRequest request);
}
