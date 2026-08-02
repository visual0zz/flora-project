package com.flora.ai.api;

/**
 * 推理配置：采样参数 + 思考模式。
 * <p>{@code temperature}/{@code topP}/{@code seed} 为经典采样参数（推理模型上可能被忽略），
 * {@code maxTokens} 为最大输出，{@code thinking} 为思考模式（关闭/自动/指定强度）。
 * 各字段为 null 表示由厂商默认值决定。</p>
 */
public record InferenceConfig(Double temperature, Double topP,
                              Long seed, Integer maxTokens, Thinking thinking) {

    public static final InferenceConfig DEFAULT =
            new InferenceConfig(null, null, null, null, Thinking.AUTO);

    /** 便捷构造：仅指定温度与最大输出 token。 */
    public static InferenceConfig of(Double temperature, Integer maxTokens) {
        return new InferenceConfig(temperature, null, null, maxTokens, Thinking.AUTO);
    }

    /** 便捷构造：仅指定思考模式。 */
    public static InferenceConfig thinking(Thinking thinking) {
        return new InferenceConfig(null, null, null, null, thinking);
    }
}
