package com.flora.ai.api;

/**
 * 采样配置：控制生成的随机性与长度。
 * <p>{@code temperature}/{@code topP}/{@code topK}/{@code seed} 为经典采样参数；
 * {@code annealing} 为温度退火（生成过程中从高温度渐降到低温度，预留特性）。
 * 各字段为 null 表示由厂商默认值决定。</p>
 */
public record SamplingConfig(Double temperature, Double topP, Integer topK,
                             Long seed, Integer maxTokens, Annealing annealing) {

    /** 温度退火：初始温度 → 结束温度。 */
    public record Annealing(double startTemp, double endTemp) {
    }

    public static final SamplingConfig DEFAULT = new SamplingConfig(null, null, null, null, null, null);

    /** 便捷构造：仅指定温度与最大输出 token。 */
    public static SamplingConfig of(Double temperature, Integer maxTokens) {
        return new SamplingConfig(temperature, null, null, null, maxTokens, null);
    }

    /** 便捷构造：带温度退火。 */
    public static SamplingConfig withAnnealing(double startTemp, double endTemp, Integer maxTokens) {
        return new SamplingConfig(null, null, null, null, maxTokens,
                new Annealing(startTemp, endTemp));
    }
}
