package com.flora.ai.api;

/**
 * 基础对话客户端：所有 AI 提供者必须实现的能力。
 * <p>通过 {@code instanceof} 检查可发现扩展能力：
 * {@link StreamingClient}（流式）、{@link JsonClient}（JSON 输出）、
 * {@link MultimodalClient}（多模态输入）、{@link ToolClient}（工具调用）。</p>
 */
public interface ChatClient {

    /** 发送对话请求，返回完整响应。 */
    ChatResponse chat(ChatRequest request);
}
