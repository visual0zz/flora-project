package com.flora.entropy.mesure.engine;

import com.flora.entropy.mesure.EntropyMetric;

import java.util.Set;

/**
 * 字母数字类别数量度。
 * <p>统计字符串中出现的字母/数字类别数（小写 / 大写 / 数字），范围 {@code [0,3]}。
 * 刻意忽略符号、分隔符（{@code - _ / + = .} 等）、空白与控制字符，
 * 因为这些在日期、路径、ID 等非密钥串里极常见，不应算作"多样性"。</p>
 */
public final class AlnumClasses implements EntropyMetric {

    /** 小写字母类别位。 */
    private static final int LOWER = 1;
    /** 大写字母类别位。 */
    private static final int UPPER = 2;
    /** 数字类别位。 */
    private static final int DIGIT = 4;

    private static final String NAME = "ALNUM_CLASSES";
    private static final Set<String> SUPPORTED = Set.of(NAME);

    @Override
    public String getAlgorithmName() {
        return NAME;
    }

    @Override
    public Set<String> supportedAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public double measure(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        int mask = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') {
                mask |= LOWER;
            } else if (c >= 'A' && c <= 'Z') {
                mask |= UPPER;
            } else if (c >= '0' && c <= '9') {
                mask |= DIGIT;
            }
        }
        return Integer.bitCount(mask);
    }
}
