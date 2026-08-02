package com.flora.ai.api.impl;

/**
 * 对话响应：文本、思考内容、token 用量、停止原因、原始响应。
 * <p>{@code raw} 为厂商原始响应（debug/高级用），{@code thinkingText} 为思考内容
 * （若厂商返回）。</p>
 */
public record ChatResponse(String text, String thinkingText,
                           TokenUsage usage, String stopReason,
                           Object raw) {

    /** 是否包含思考内容。 */
    public boolean isThinking() {
        return thinkingText != null && !thinkingText.isEmpty();
    }
}
