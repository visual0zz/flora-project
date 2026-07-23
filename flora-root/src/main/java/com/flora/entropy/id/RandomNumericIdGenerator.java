package com.flora.entropy.id;

import com.flora.entropy.LongIdGenerator;
import com.flora.entropy.StringIdGenerator;
import com.flora.java.CheckUtil;

import java.security.SecureRandom;

/**
 * 纯数字随机 ID 生成器。
 * <p>
 * 同时实现了 {@link StringIdGenerator}、{@link LongIdGenerator}
 * 两个接口，生成的 ID 在字符串表示中仅包含数字 0-9，
 * 不含任何字母（a-f 等），适用于要求 ID 全部由数字构成的场景。
 */
public class RandomNumericIdGenerator implements StringIdGenerator, LongIdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 字符串 ID 的长度（位数）。 */
    private final int length;

    public RandomNumericIdGenerator() {
        this(16);
    }

    public RandomNumericIdGenerator(int length) {
        CheckUtil.mustTrue(length > 0, "长度必须大于 0");
        this.length = length;
    }
    @Override
    public String nextStrId() {
        // 生成长度为 length 的纯数字字符串
        var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    @Override
    public long nextLongId() {
        // 固定生成 64 位值：每个 4 位半字节均为 0-9 的随机数字，
        // 因此其十六进制表示仅含数字、不含字母 a-f。length 参数不影响本方法。
        long result = 0;
        for (int i = 0; i < 16; i++) {
            result = (result << 4) | RANDOM.nextInt(10);
        }
        return result;
    }
}
