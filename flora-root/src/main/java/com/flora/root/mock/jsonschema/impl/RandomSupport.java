package com.flora.root.mock.jsonschema.impl;

import java.math.BigDecimal;
import java.util.random.RandomGenerator;

/**
 * 随机生成辅助：随机字符串/数字/布尔/null/类型选择。
 */
public final class RandomSupport {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    private static final String ALNUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final RandomGenerator random;

    public RandomSupport(RandomGenerator random) {
        this.random = random;
    }

    /** 随机小写字母字符串。 */
    String randomAlpha(int length) {
        return randomFrom(ALPHABET, length);
    }

    /** 随机字母数字字符串。 */
    String randomAlnum(int length) {
        return randomFrom(ALNUM, length);
    }

    /** 随机可打印字符串（含常见符号）。 */
    String randomAscii(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) (33 + random.nextInt(94))); // '!'..'~'
        }
        return sb.toString();
    }

    private String randomFrom(String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    int intBetween(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    /** [min, max] 闭区间均匀取长整数。 */
    long longBetween(long min, long max) {
        if (max <= min) {
            return min;
        }
        long span = max - min;
        if (span == Long.MAX_VALUE) {
            return min + random.nextLong(Long.MAX_VALUE);
        }
        if (span < 0) {
            // 跨度超出 long 可表示范围：用 double 近似
            return min + (long) (random.nextDouble() * Long.MAX_VALUE);
        }
        return min + random.nextLong(span + 1);
    }

    BigDecimal decimalBetween(BigDecimal min, BigDecimal max) {
        if (min.compareTo(max) >= 0) {
            return min;
        }
        BigDecimal range = max.subtract(min);
        return min.add(range.multiply(BigDecimal.valueOf(random.nextDouble())));
    }

    boolean nextBoolean() {
        return random.nextBoolean();
    }

    double nextDouble() {
        return random.nextDouble();
    }

    RandomGenerator random() {
        return random;
    }
}
