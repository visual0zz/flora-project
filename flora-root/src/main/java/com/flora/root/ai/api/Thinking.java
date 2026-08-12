package com.flora.root.ai.api;

/**
 * 思考模式：是否启用推理思考及强度。
 * <p>对应各家"推理模式"：OpenAI o 系列 reasoning effort、Anthropic Extended Thinking、
 * Gemini Thinking、DeepSeek reasoner。{@code AUTO} 由模型/接口自动决定，
 * {@code LOW}~{@code MAX} 指定思考强度。</p>
 */
public enum Thinking {
    /** 关闭思考。 */
    OFF,
    /** 由模型/接口自动决定。 */
    AUTO,
    /** 思考强度：低。 */
    LOW,
    /** 思考强度：中。 */
    MEDIUM,
    /** 思考强度：高。 */
    HIGH,
    /** 思考强度：最高。 */
    MAX
}
