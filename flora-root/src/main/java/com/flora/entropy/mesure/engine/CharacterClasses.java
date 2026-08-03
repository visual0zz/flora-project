package com.flora.entropy.mesure.engine;

import com.flora.entropy.mesure.EntropyMetric;

import java.util.Set;

/**
 * 字符类别数量度。
 * <p>统计字符串中出现的字符大类数量（小写 / 大写 / 数字 / 符号），
 * 空白字符与控制字符不计入任何类别；返回值在 {@code [0,4]}。</p>
 */
public final class CharacterClasses implements EntropyMetric {

    /** 小写字母类别位。 */
    private static final int LOWER = 1;
    /** 大写字母类别位。 */
    private static final int UPPER = 2;
    /** 数字类别位。 */
    private static final int DIGIT = 4;
    /** 符号（非字母数字、非空白、非控制字符）类别位。 */
    private static final int SYMBOL = 8;

    private static final String NAME = "CHARACTER_CLASSES";
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
            } else if (!Character.isWhitespace(c)
                    && !Character.isISOControl(c)
                    && !Character.isLetterOrDigit(c)) {
                mask |= SYMBOL;
            }
        }
        return Integer.bitCount(mask);
    }
}
