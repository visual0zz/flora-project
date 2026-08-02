package com.flora.ai.api.impl;

/**
 * 流式事件：增量输出、思考增量或流结束。
 * <p>{@code type} 区分文本增量/思考增量/完成；{@code text} 为本次增量内容
 * （完成事件时可为 null，最终结果在 {@link #finishReason}）。</p>
 */
public record StreamEvent(Type type, String text, String finishReason) {

    public enum Type {
        /** 文本增量。 */
        TEXT,
        /** 思考内容增量。 */
        THINKING,
        /** 流结束。 */
        DONE
    }

    public static StreamEvent text(String delta) {
        return new StreamEvent(Type.TEXT, delta, null);
    }

    public static StreamEvent thinking(String delta) {
        return new StreamEvent(Type.THINKING, delta, null);
    }

    public static StreamEvent done(String finishReason) {
        return new StreamEvent(Type.DONE, null, finishReason);
    }
}
