package com.flora.entropy.id;

import com.flora.entropy.StringIdGenerator;
import com.flora.java.CheckUtil;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;



/**
 * 128 位 Snowflake ID 生成器，输出 32 字符的十六进制字符串。
 *
 * <p><b>ID 整体布局</b>（128 位，hex 输出时 high 在前）：
 * <table>
 *   <tr><th>段</th><th>保留位 (high)</th><th>时间戳 (high)</th><th>机器 ID (high)</th><th>自增序列 (low)</th><th>业务类型</th><th>对象编号</th><th>回拨计数</th><th>回拨随机数</th></tr>
 *   <tr><td>位宽</td><td>1 位</td><td>44 位</td><td>19 位</td><td>16 位</td><td>9 位</td><td>7 位</td><td>4 位</td><td>28 位</td></tr>
 * </table>
 *
 * <p><b>高 64 位</b>（high）：
 * <pre>
 * 位 63   ：1 位保留位，固定为 0（保证最高位不为 1，避免被当作负数 long）
 * 位 62-19：44 位相对时间戳（毫秒，相对自定义纪元 EPOCH，可覆盖约 557 年）
 * 位 18-0 ：19 位机器 ID（支持最多 2¹⁹ 个节点 / 机器）
 * </pre>
 *
 * <p><b>低 64 位</b>（low）：
 * <pre>
 * 位 63-48：16 位自增序列号（同一毫秒内的请求递增，溢出后自旋等待下一毫秒）
 * 位 47-39：9 位业务类型（支持 512 种业务场景隔离）
 * 位 38-32：7 位生成器对象编号（每新建一个对象递增 1，溢出回卷，用于区分同进程内不同实例）
 * 位 31-28：4 位回拨计数（时钟回拨 &gt;100ms 时递增，溢出归零，记录回拨次数）
 * 位 27-0 ：28 位回拨随机数（时钟回拨 &gt;100ms 时重新随机，增强唯一性）
 * </pre>
 *
 * <p><b>时间回拨处理策略</b>：
 * <ul>
 *   <li>回拨 &le; 100ms：睡眠等待，直至系统时钟追平 lastTimestamp</li>
 *   <li>回拨 &gt; 100ms：递增回拨计数（4 bit，溢出归零），重新随机生成回拨随机数，
 *       以新状态继续生成 ID。由于回拨随机数（28 bit）与回拨计数（4 bit）随机熵有限，
 *       此机制为<b>尽力保证</b>而非绝对保证——在多次回拨且时钟恰好回到同一历史时间线、
 *       同时随机值撞上历史值的极小概率下，仍可能产生重复 ID。</li>
 * </ul>
 *
 * <p><b>时钟回拨处理</b>：当时钟回拨超过容忍范围（{@code MAX_BACKWARD_MS}）时不抛异常，
 * 而是切换回拨状态继续生成，以获得可用性，作为代价回拨场景下生成的id只有概率意义上的唯一性。
 *
 * <p><b>时钟约束</b>：要求系统时钟不早于自定义纪元 {@code EPOCH}；若早于 EPOCH，
 * 相对时间戳为负，位移后时间戳字段将出现负值，导致 ID 异常。
 *
 * <p>线程安全：方法级 {@code synchronized} 同步。注意回拨等待期间的 {@code Thread.sleep}
 * 在持锁状态下进行，单实例高并发触发回拨时会阻塞同实例其它调用，建议每线程 / 每节点持有一个实例。
 *
 * <p>输出格式：32 字符小写十六进制字符串，高 64 位在前、低 64 位在后。
 */
public final class Snowflake128Generator implements StringIdGenerator {

    // ========================================================================
    // 位宽定义
    // ========================================================================
    // 高 64 位：1(保留位) + 44(时间戳) + 19(机器 ID) = 64
    // 低 64 位：16(序列) + 9(业务类型) + 7(对象编号) + 4(回拨计数) + 28(回拨随机数) = 64

    // ---- 高 64 位相关 ----
    /** 时间戳位宽（44 位，相对 EPOCH 可覆盖约 557 年）。 */
    private static final int TIMESTAMP_BITS = 44;
    /** 机器 ID 位宽（19 位，支持最多 524,288 个节点 / 机器）。 */
    private static final int MACHINE_ID_BITS = 19;

    // ---- 低 64 位相关 ----
    /** 自增序列位宽（16 位，同一毫秒最多 65536 个 ID）。 */
    private static final int SEQUENCE_BITS = 16;
    /** 业务类型位宽（9 位，支持 512 种业务场景隔离）。 */
    private static final int BIZ_TYPE_BITS = 9;
    /** 生成器对象编号位宽（7 位，每新建一个生成器对象递增 1，溢出回卷）。 */
    private static final int GEN_OBJ_NUMBER_BITS = 7;
    /** 回拨计数位宽（4 位，0~15，溢出归零）。 */
    private static final int BACKOFF_COUNT_BITS = 4;
    /** 回拨随机数位宽（28 位，268,435,456 种随机值，用于增强回拨场景下的唯一性）。 */
    private static final int BACKOFF_RANDOM_BITS = 28;

    // ========================================================================
    // 位移量（各自 long 内的低位对齐）
    // ========================================================================

    // low 64 位内部布局（低位→高位）：回拨随机数 → 回拨计数 → 对象编号 → 业务类型 → 序列号
    private static final int BACKOFF_RANDOM_SHIFT = 0;
    private static final int BACKOFF_COUNT_SHIFT = BACKOFF_RANDOM_SHIFT + BACKOFF_RANDOM_BITS;
    private static final int GEN_OBJ_NUMBER_SHIFT = BACKOFF_COUNT_SHIFT + BACKOFF_COUNT_BITS;
    private static final int BIZ_TYPE_SHIFT = GEN_OBJ_NUMBER_SHIFT + GEN_OBJ_NUMBER_BITS;
    private static final int SEQUENCE_SHIFT = BIZ_TYPE_SHIFT + BIZ_TYPE_BITS;

    // high 64 位内部布局（低位→高位）：机器 ID → 时间戳（位 63 固定保留为 0）
    private static final int MACHINE_ID_SHIFT = 0;
    private static final int TIMESTAMP_SHIFT = MACHINE_ID_SHIFT + MACHINE_ID_BITS;

    // ========================================================================
    // 掩码
    // ========================================================================

    /** 机器 ID 掩码（低 19 位），用于构造时截断有效位。 */
    private static final long MACHINE_ID_MASK = (1L << MACHINE_ID_BITS) - 1;
    /** 序列号掩码（低 16 位），用于溢出回卷。 */
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    /** 生成器对象编号掩码（低 7 位），用于从全局递增计数中取有效位。 */
    private static final long GEN_OBJ_NUMBER_MASK = (1L << GEN_OBJ_NUMBER_BITS) - 1;
    /** 回拨计数掩码（低 4 位），用于溢出回卷。 */
    private static final long BACKOFF_COUNT_MASK = (1L << BACKOFF_COUNT_BITS) - 1;
    /** 回拨随机数掩码（低 28 位），用于生成回拨随机值时截断有效位。 */
    private static final long BACKOFF_RANDOM_MASK = (1L << BACKOFF_RANDOM_BITS) - 1;

    // ========================================================================
    // 常量
    // ========================================================================

    /**
     * 自定义纪元时间戳（2023-11-14 21:53:20 GMT），单位毫秒。
     * 相对时间戳以该纪元为基准，可延长时间戳字段的可用范围。
     */
    private static final long EPOCH = 1700000000000L;

    /**
     * 最大容忍回拨时长（毫秒）。
     * 回拨不超过此值时通过不可中断睡眠等待时钟追平；
     * 超过此值则视为重大回拨，切换回拨状态继续生成。
     */
    private static final long MAX_BACKWARD_MS = 100L;

    /** 全局共享的加密安全随机数生成器。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成器对象全局编号计数器。每创建一个 {@link Snowflake128Generator} 实例就递增 1，
     * 取其低 7 位作为该实例的「生成器对象编号」。计数溢出回卷即可，无需特殊处理。
     */
    private static final AtomicInteger GEN_OBJ_SEQ = new AtomicInteger(0);

    // ========================================================================
    // 实例字段
    // ========================================================================

    /** 机器 ID（19 位，构造时注入或随机分配）。 */
    private final long machineId;
    /** 生成器对象编号（7 位，构造时由全局计数器分配，每新建一个对象递增 1）。 */
    private final long genObjNumber;
    /** 业务类型（9 位，构造时注入，默认 0）。 */
    private final long bizType;

    /**
     * 回拨随机数（28 位有效值）。
     * 初始值在构造时由 SecureRandom 生成；
     * 当发生 >100ms 的时钟回拨时重新随机，尽力保证回拨期间 ID 的唯一性（概率性，非绝对）。
     */
    private long backoffRandom;
    /**
     * 回拨发生次数（4 位，0~15，溢出归零）。
     * 每次检测到 >100ms 的时钟回拨时递增，
     * 与 backoffRandom 配合尽力保证回拨场景下 ID 不重复（概率性，非绝对）。
     */
    private long backoffCount;

    /** 上一次生成 ID 的时间戳（毫秒）。初始为 -1，首次调用时直接取当前时间。 */
    private long lastTimestamp = -1L;
    /** 同一毫秒内的自增序列号（0~65535）。跨毫秒时归零。 */
    private long sequence = 0L;

    /**
     * 创建生成器，机器 ID 由 {@link SecureRandom} 随机分配，业务类型为 0。
     */
    public Snowflake128Generator() {
        this(RANDOM.nextLong() & MACHINE_ID_MASK, 0);
    }

    /**
     * 创建生成器，指定机器 ID 和业务类型。
     *
     * @param machineId 机器 ID（0 到 2^19-1）
     * @param bizType 业务类型（0 到 511）
     */
    public Snowflake128Generator(long machineId, long bizType) {
        CheckUtil.mustTrue(machineId >= 0 && machineId < (1L << MACHINE_ID_BITS),
                "machineId 必须在 0 到 " + ((1L << MACHINE_ID_BITS) - 1) + " 之间");
        CheckUtil.mustTrue(bizType >= 0 && bizType < (1L << BIZ_TYPE_BITS),
                "bizType 必须在 0 到 " + ((1L << BIZ_TYPE_BITS) - 1) + " 之间");
        this.machineId = machineId;
        this.genObjNumber = GEN_OBJ_SEQ.getAndIncrement() & GEN_OBJ_NUMBER_MASK;
        this.bizType = bizType;
        this.backoffRandom = RANDOM.nextLong() & BACKOFF_RANDOM_MASK;
        this.backoffCount = 0;
    }

    /**
     * 生成下一个 128 位 ID 的十六进制字符串（32 字符）。
     * <p>线程安全：方法级 {@code synchronized} 同步。</p>
     *
     * @return 32 字符的十六进制字符串
     */
    @Override
    public synchronized String nextStrId() {
        long[] parts = nextRaw();
        return String.format("%016x%016x", parts[0], parts[1]);
    }

    /**
     * 生成下一个 128 位 ID 的原始高低两部分。
     *
     * @return {@code long[2]}，下标 0 为高 64 位、下标 1 为低 64 位
     */
    private long[] nextRaw() {
        long now = System.currentTimeMillis();
        sequence = (sequence + 1) & SEQUENCE_MASK;
        if (now != lastTimestamp || sequence == 0) {
            now = waitUntil(lastTimestamp + 1);
            sequence = 0;
        }
        lastTimestamp = now;
        long high = ((now - EPOCH) << TIMESTAMP_SHIFT) | (machineId << MACHINE_ID_SHIFT);
        long low = (sequence << SEQUENCE_SHIFT)
                | (bizType << BIZ_TYPE_SHIFT)
                | (genObjNumber << GEN_OBJ_NUMBER_SHIFT)
                | (backoffCount << BACKOFF_COUNT_SHIFT)
                | (backoffRandom << BACKOFF_RANDOM_SHIFT);
        return new long[]{high, low};
    }

    /**
     * 等待系统时钟达到 target（含），过程中处理时钟回拨。
     * <p>距 target 较远（&gt;1ms）时睡眠等待，临近时自旋；被中断仅恢复中断标记并重新采样。</p>
     * <p>等待期间若检测到时钟回拨超过 {@value #MAX_BACKWARD_MS}ms，不抛异常，而是递增回拨计数、
     * 重新随机回拨随机数（切换回拨状态），并直接以当前回拨时间继续生成，
     * 尽力保证（概率性，非绝对）回拨场景下 ID 不重复。</p>
     *
     * @param target 目标时间戳（调用方传入 lastTimestamp + 1）
     * @return 不小于 target 的时间戳；大回拨时返回当前回拨时间（&lt; target）
     */
    private long waitUntil(long target) {
        long now;
        do {
            now = System.currentTimeMillis();
            long backward = target - now;
            if (backward > MAX_BACKWARD_MS) {
                // 大范围回拨：切换回拨状态，以当前回拨时间继续生成（不等待）
                backoffCount = (backoffCount + 1) & BACKOFF_COUNT_MASK;
                backoffRandom = RANDOM.nextLong() & BACKOFF_RANDOM_MASK;
                return now;
            } else if (backward > 1) {
                try {
                    Thread.sleep(backward);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } while (now < target);
        return now;
    }
}
