package com.flora.entropy;

import com.flora.entropy.id.RandomNumericIdGenerator;
import com.flora.entropy.id.Snowflake64Generator;
import com.flora.entropy.id.Snowflake128Generator;

/**
 * ID 生成工具类。
 * <p>提供多种 ID 生成方式，并支持链式构建：
 * <pre>
 * IdUtil.stringId().numeric(10).nextStrId();
 * IdUtil.longId().snowflake(111).nextLongId();
 * </pre>
 * </p>
 */
public final class IdUtil {
    private IdUtil() {}

    // ========================================================================
    // 链式入口
    // ========================================================================

    /** 进入字符串 ID 构建器。 */
    public static StringIdStep stringId() {
        return new StringIdStep();
    }

    /** 进入长整型 ID 构建器。 */
    public static LongIdStep longId() {
        return new LongIdStep();
    }

    // ========================================================================
    // 构建器
    // ========================================================================

    /** 字符串 ID 构建器：选择具体的字符串 ID 生成策略，最终以 {@link StringIdGenerator#nextStrId()} 产出。 */
    public static final class StringIdStep {
        private StringIdStep() {}

        /** 纯数字随机字符串生成器，length 为位数。 */
        public StringIdGenerator numeric(int length) {
            return new RandomNumericIdGenerator(length);
        }

        /** 128 位雪花算法字符串生成器（32 字符十六进制），machineId 与 bizType 见 {@link Snowflake128Generator}。 */
        public StringIdGenerator snowflake(long machineId, long bizType) {
            return new Snowflake128Generator(machineId, bizType);
        }

    }

    /** 长整型 ID 构建器：选择具体的长整型 ID 生成策略，最终以 {@link LongIdGenerator#nextLongId()} 产出。 */
    public static final class LongIdStep {
        private LongIdStep() {}

        /** 64 位雪花算法生成器（美团版位分配），workerId（0~1023）见 {@link Snowflake64Generator}。 */
        public LongIdGenerator snowflake(long workerId) {
            return new Snowflake64Generator(workerId);
        }

        /** 纯数字随机长整型生成器。nextLongId() 固定为 64 位值（半字节均为 0-9），length 仅影响 nextStrId() 的位数。 */
        public LongIdGenerator numeric(int length) {
            return new RandomNumericIdGenerator(length);
        }
    }
}
