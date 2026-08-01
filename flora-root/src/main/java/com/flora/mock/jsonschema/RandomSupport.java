package com.flora.mock.jsonschema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.random.RandomGenerator;

/**
 * 随机生成辅助：随机字符串/数字/布尔/null/类型选择。
 */
final class RandomSupport {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
    private static final String ALNUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final RandomGenerator random;

    RandomSupport(RandomGenerator random) {
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

    long longBetween(long min, long max) {
        if (max <= min) {
            return min;
        }
        return min + (long) (random.nextDouble() * (max - min));
    }

    BigDecimal decimalBetween(BigDecimal min, BigDecimal max) {
        if (min.compareTo(max) >= 0) {
            return min;
        }
        BigDecimal range = max.subtract(min);
        return min.add(range.multiply(BigDecimal.valueOf(random.nextDouble())));
    }

    BigInteger integerBetween(BigDecimal min, BigDecimal max) {
        return decimalBetween(min, max).toBigInteger();
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
