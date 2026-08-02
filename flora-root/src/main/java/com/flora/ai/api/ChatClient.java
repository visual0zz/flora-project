package com.flora.ai.api;

/**
 * 基础对话客户端：所有 AI 提供者必须实现的能力。
 * <p>通过 {@link #capabilities()} 可查询本 client 支持的能力（思考/JSON/多模态/工具）。</p>
 */
public interface ChatClient extends ApiClient {

    /** 发送对话请求，返回完整响应。 */
    ChatResponse chat(ChatRequest request);
}
