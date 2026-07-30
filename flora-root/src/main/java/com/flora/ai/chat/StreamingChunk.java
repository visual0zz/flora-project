package com.flora.ai.chat;

import java.util.List;

/** LLM 流式响应片段。 */
public record StreamingChunk(
    String delta,
    List<ToolCall> toolCallDeltas,
    FinishReason finishReason,
    TokenUsage usage
) {
    public StreamingChunk(String delta) {
        this(delta, List.of(), null, null);
    }

    public boolean isDone() {
        return finishReason != null;
    }
}
