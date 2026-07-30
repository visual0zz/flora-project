package com.flora.ai.chat;

import java.util.List;

/** LLM 聊天请求。 */
public record ChatRequest(List<ChatMessage> messages, LlmOptions options) {

    public ChatRequest(List<ChatMessage> messages) {
        this(messages, new LlmOptions());
    }
}
