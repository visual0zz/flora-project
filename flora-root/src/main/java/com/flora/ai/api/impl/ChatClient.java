package com.flora.ai.api.impl;

/**
 * 基础对话客户端：所有 AI 提供者必须实现的能力。
 * <p>通过 {@code instanceof} 检查可发现扩展能力：
 * {@link StreamingClient}（流式）、{@link JsonClient}（JSON 输出）、
 * {@link MultimodalClient}（多模态输入）。</p>
 */
public interface ChatClient {

    /** 发送对话请求，返回完整响应。 */
    ChatResponse chat(ChatRequest request);

    /** 便捷：发送请求并直接返回文本结果。 */
    default String ask(ChatRequest request) {
        return chat(request).text();
    }
}
