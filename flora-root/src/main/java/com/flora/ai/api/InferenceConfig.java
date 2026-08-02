package com.flora.ai.api;

/**
 * 推理配置：采样种子 + 输出长度 + 思考模式。
 * <p>{@code seed} 为采样种子（可复现性），{@code maxTokens} 为最大输出，
 * {@code thinking} 为思考模式（关闭/自动/指定强度）。推理模型趋向确定性输出，
 * 经典采样参数（temperature/topP）已从模型层弱化，故不在此建模。
 * 各字段为 null 表示由厂商默认值决定。</p>
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
