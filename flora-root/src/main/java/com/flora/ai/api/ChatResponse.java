package com.flora.ai.api;

import java.util.List;

/**
 * 对话响应：文本、思考内容、工具调用、token 用量、停止原因、多模态输出、原始响应。
 * <p>{@code raw} 为厂商原始响应（debug/高级用），{@code thinkingText} 为思考内容
 * （若厂商返回），{@code toolCalls} 为模型发起的工具调用（若请求含 tools），
 * {@code outputs} 为模型产生的多模态内容（文本统一为 {@link ContentBlock.Text}，
 * 也含图片/音频/文件；无多模态时为空列表）。</p>
 */
public record ChatResponse(String text, String thinkingText,
                           List<ToolCall> toolCalls,
                           TokenUsage usage, String stopReason,
                           List<ContentBlock> outputs,
                           Object raw) {

    /** 兼容构造：无多模态输出（outputs 为空）。 */
    public ChatResponse(String text, String thinkingText,
                        List<ToolCall> toolCalls,
                        TokenUsage usage, String stopReason,
                        Object raw) {
        this(text, thinkingText, toolCalls, usage, stopReason, List.of(), raw);
    }

    /** 是否包含思考内容。 */
    public boolean isThinking() {
        return thinkingText != null && !thinkingText.isEmpty();
    }

    /** 是否包含工具调用。 */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /** 是否包含多模态输出（非文本块）。 */
    public boolean hasMultimodalOutputs() {
        if (outputs == null) {
            return false;
        }
        for (ContentBlock b : outputs) {
            if (!(b instanceof ContentBlock.Text)) {
                return true;
            }
        }
        return false;
    }
}
