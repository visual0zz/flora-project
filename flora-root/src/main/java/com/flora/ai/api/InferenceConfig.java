package com.flora.ai.api;

/**
 * 推理配置：采样参数 + 思考模式。
 * <p>{@code temperature}/{@code topP}/{@code topK}/{@code seed} 为经典采样参数，
 * {@code maxTokens} 为最大输出，{@code thinking} 为思考模式（关闭/自动/指定强度），
 * {@code annealing} 为温度退火（生成过程中从高温度渐降到低温度，预留特性）。
 * 各字段为 null 表示由厂商默认值决定。</p>
 */
public record InferenceConfig(Double temperature, Double topP, Integer topK,
                              Long seed, Integer maxTokens, Thinking thinking,
                              Annealing annealing) {

    /** 温度退火：初始温度 → 结束温度。 */
    public record Annealing(double startTemp, double endTemp) {
    }

    public static final InferenceConfig DEFAULT =
            new InferenceConfig(null, null, null, null, null, Thinking.AUTO, null);

    /** 便捷构造：仅指定温度与最大输出 token。 */
    public static InferenceConfig of(Double temperature, Integer maxTokens) {
        return new InferenceConfig(temperature, null, null, null, maxTokens, Thinking.AUTO, null);
    }

    /** 便捷构造：带温度退火。 */
    public static InferenceConfig withAnnealing(double startTemp, double endTemp, Integer maxTokens) {
        return new InferenceConfig(null, null, null, null, maxTokens, Thinking.AUTO,
                new Annealing(startTemp, endTemp));
    }

    /** 便捷构造：仅指定思考模式。 */
    public static InferenceConfig thinking(Thinking thinking) {
        return new InferenceConfig(null, null, null, null, null, thinking, null);
    }
}
