package com.flora.ai.chat;

import java.util.List;

/** LLM 完整响应。 */
public record ChatResponse(
    List<ChatChoice> choices,
    TokenUsage usage,
    String model,
    long latencyMs
) {
    public ChatMessage message() {
        return choices.isEmpty() ? null : choices.get(0).message();
    }

    public String content() {
        ChatMessage m = message();
        return m == null ? null : m.content();
    }

    public List<ToolCall> toolCalls() {
        ChatMessage m = message();
        return m == null ? List.of() : m.toolCalls();
    }

    public FinishReason finishReason() {
        return choices.isEmpty() ? FinishReason.ERROR : choices.get(0).finishReason();
    }
}
