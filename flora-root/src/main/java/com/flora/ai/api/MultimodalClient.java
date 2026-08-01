package com.flora.ai.api;

/**
 * 多模态输入能力（可选）：请求可含图片/音频/文件内容块。
 * <p>支持多模态输入的客户端实现此接口。请求中的消息内容块可含
 * {@code Image}/{@code Audio}/{@code File} 块。</p>
 */
public interface MultimodalClient {

    /** 发送多模态请求（消息内容块可含图片/音频/文件），返回完整响应。 */
    ChatResponse chatMultimodal(ChatRequest request);
}
