package com.flora.ai.api.impl;

/**
 * 思考配置：是否启用推理思考、思考强度、预算。
 * <p>对应各家"推理模式"：OpenAI o 系列 reasoning effort、Anthropic Extended Thinking、
 * Gemini Thinking。新枚举值即新能力（向前兼容）。</p>
 */
public record ThinkingConfig(Mode mode, Effort effort, Integer budgetTokens) {

    public enum Mode {
        /** 关闭思考。 */
        OFF,
        /** 启用思考。 */
        ON,
        /** 由模型/接口自动决定。 */
        AUTO
    }

    public enum Effort {
        LOW, MEDIUM, HIGH, MAX
    }

    public static ThinkingConfig of(Mode mode, Effort effort) {
        return new ThinkingConfig(mode, effort, null);
    }

    /** 关闭思考。 */
    public static ThinkingConfig off() {
        return new ThinkingConfig(Mode.OFF, null, null);
    }

    /** 自动决定思考模式。 */
    public static ThinkingConfig auto() {
        return new ThinkingConfig(Mode.AUTO, null, null);
    }

    /** 是否启用思考。 */
    public boolean enabled() {
        return mode == Mode.ON;
    }
}
