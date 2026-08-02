package com.flora.ai.api;

import java.util.List;

/**
 * 对话响应：文本、思考内容、工具调用、token 用量、停止原因、原始响应。
 * <p>{@code raw} 为厂商原始响应（debug/高级用），{@code thinkingText} 为思考内容
 * （若厂商返回），{@code toolCalls} 为模型发起的工具调用（若请求含 tools）。</p>
 */
public record ChatResponse(String text, String thinkingText,
                           List<ToolCall> toolCalls,
                           TokenUsage usage, String stopReason,
                           Object raw) {

    /** 是否包含思考内容。 */
    public boolean isThinking() {
        return thinkingText != null && !thinkingText.isEmpty();
    }

    /** 是否包含工具调用。 */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
