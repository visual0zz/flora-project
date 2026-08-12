package com.flora.root.ai.api;

/**
 * 推理配置：采样种子 + 输出长度 + 思考模式。
 * <p>承载 {@code seed}（采样种子，可复现性）、{@code maxTokens}（最大输出长度）、
 * {@code thinking}（思考模式）。各字段为 null 表示由厂商默认值决定。</p>
 */
public record InferenceConfig(Long seed, Integer maxTokens, Thinking thinking) {

    public static final InferenceConfig DEFAULT =
            new InferenceConfig(null, null, Thinking.AUTO);

    /** 便捷构造：仅指定最大输出 token。 */
    public static InferenceConfig of(Integer maxTokens) {
        return new InferenceConfig(null, maxTokens, Thinking.AUTO);
    }

    /** 便捷构造：仅指定思考模式。 */
    public static InferenceConfig thinking(Thinking thinking) {
        return new InferenceConfig(null, null, thinking);
    }
}
