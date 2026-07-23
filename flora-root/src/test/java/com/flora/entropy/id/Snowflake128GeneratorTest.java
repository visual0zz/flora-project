package com.flora.entropy.id;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Snowflake128Generator} 的单元测试。
 * ID 为 32 字符十六进制字符串，对应 128 位：
 * <pre>
 * 高 64 位（hex 前 16 字符）：
 *   位 63      ：1 位保留位，固定为 0
 *   位 62-19   ：44 位相对时间戳（ms，相对 EPOCH）
 *   位 18-0    ���19 位机器 ID
 *
 * 低 64 位（hex 后 16 字符）：
 *   位 63-48   ：16 位自增序列号
 *   位 47-39   ：9 位业务类型
 *   位 38-32   ：7 位生成器对象编号
 *   位 31-28   ：4 位回拨计数
 *   位 27-0    ：28 位回拨随机数
 * </pre>
 */
class Snowflake128GeneratorTest {

    private static final int MACHINE_ID_BITS = 19;
    private static final int TIMESTAMP_BITS = 44;
    private static final int SEQUENCE_BITS = 16;
    private static final int BIZ_TYPE_BITS = 9;
    private static final int GEN_OBJ_NUMBER_BITS = 7;
    private static final int BACKOFF_COUNT_BITS = 4;
    private static final int BACKOFF_RANDOM_BITS = 28;

    // low 部分位移
    private static final int BACKOFF_RANDOM_SHIFT = 0;
    private static final int BACKOFF_COUNT_SHIFT = BACKOFF_RANDOM_SHIFT + BACKOFF_RANDOM_BITS;
    private static final int GEN_OBJ_NUMBER_SHIFT = BACKOFF_COUNT_SHIFT + BACKOFF_COUNT_BITS;
    private static final int BIZ_TYPE_SHIFT = GEN_OBJ_NUMBER_SHIFT + GEN_OBJ_NUMBER_BITS;
    private static final int SEQUENCE_SHIFT = BIZ_TYPE_SHIFT + BIZ_TYPE_BITS;

    // high 部分位移
    private static final int MACHINE_ID_SHIFT = 0;
    private static final int TIMESTAMP_SHIFT = MACHINE_ID_SHIFT + MACHINE_ID_BITS;

    // 掩码
    private static final long MACHINE_ID_MASK = (1L << MACHINE_ID_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final long BIZ_TYPE_MASK = (1L << BIZ_TYPE_BITS) - 1;
    private static final long GEN_OBJ_NUMBER_MASK = (1L << GEN_OBJ_NUMBER_BITS) - 1;
    private static final long BACKOFF_COUNT_MASK = (1L << BACKOFF_COUNT_BITS) - 1;
    private static final long BACKOFF_RANDOM_MASK = (1L << BACKOFF_RANDOM_BITS) - 1;
    private static final long TIMESTAMP_MASK = (1L << TIMESTAMP_BITS) - 1;

    private static final Pattern HEX32 = Pattern.compile("[0-9a-f]{32}");

    // ========================================================================
    // 构造测试
    // ========================================================================

    @Test
    void defaultConstructorSucceeds() {
        Snowflake128Generator gen = new Snowflake128Generator();
        assertNotNull(gen);
        for (int i = 0; i < 100; i++) {
            assertNotNull(new Snowflake128Generator());
        }
    }

    @Test
    void constructorWithValidMachineIdAndBizType() {
        Snowflake128Generator gen = new Snowflake128Generator(1, 0);
        assertNotNull(gen);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, -100, 1L << MACHINE_ID_BITS, 1L << 20})
    void constructorWithInvalidMachineIdThrows(long machineId) {
        assertThrows(IllegalArgumentException.class,
                () -> new Snowflake128Generator(machineId, 0));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 512, 1023})
    void constructorWithInvalidBizTypeThrows(long bizType) {
        assertThrows(IllegalArgumentException.class,
                () -> new Snowflake128Generator(1, bizType));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, (1L << MACHINE_ID_BITS) - 1})
    void constructorWithBoundaryMachineId(long machineId) {
        Snowflake128Generator gen = new Snowflake128Generator(machineId, 0);
        assertNotNull(gen);
    }

    // ========================================================================
    // 基本生成测试
    // ========================================================================

    @Test
    void nextStrIdReturns32CharHex() {
        Snowflake128Generator gen = new Snowflake128Generator(1, 0);
        String id = gen.nextStrId();
        assertEquals(32, id.length(), "128 位 ID 应输出 32 字符十六进制字符串");
        assertTrue(HEX32.matcher(id).matches());
    }

    @Test
    void consecutiveIdsAreUnique() {
        Snowflake128Generator gen = new Snowflake128Generator(1, 0);
        Set<String> ids = new HashSet<>();
        int count = 10_000;
        for (int i = 0; i < count; i++) {
            assertTrue(ids.add(gen.nextStrId()), "产生重复 ID");
        }
        assertEquals(count, ids.size());
    }

    // ========================================================================
    // 位结构验证
    // ========================================================================

    @Test
    void reservedBitIsAlwaysZero() {
        Snowflake128Generator gen = new Snowflake128Generator(1, 0);
        for (int i = 0; i < 1000; i++) {
            String id = gen.nextStrId();
            long high = Long.parseUnsignedLong(id.substring(0, 16), 16);
            assertTrue(high >= 0, "高 64 位���高位（保留位）必须为 0 -> 高 64 位不应为负数");
        }
    }

    @Test
    void machineIdFieldIsCorrect() {
        long expectedMachineId = 42;
        Snowflake128Generator gen = new Snowflake128Generator(expectedMachineId, 0);
        String id = gen.nextStrId();
        long high = Long.parseUnsignedLong(id.substring(0, 16), 16);
        long extractedMachineId = (high >>> MACHINE_ID_SHIFT) & MACHINE_ID_MASK;
        assertEquals(expectedMachineId, extractedMachineId, "机器 ID 字段应与构造参数一致");
    }

    @Test
    void bizTypeFieldIsCorrect() {
        long expectedBizType = 127;
        Snowflake128Generator gen = new Snowflake128Generator(1, expectedBizType);
        String id = gen.nextStrId();
        long low = Long.parseUnsignedLong(id.substring(16), 16);
        long extractedBizType = (low >>> BIZ_TYPE_SHIFT) & BIZ_TYPE_MASK;
        assertEquals(expectedBizType, extractedBizType, "业务类型字段应与构造参数一致");
    }

    @Test
    void sequenceFieldIncrementsWithinSameMs() {
        Snowflake128Generator gen = new Snowflake128Generator(1, 0);
        long prevSeq = -1;
        for (int i = 0; i < 100; i++) {
            String id = gen.nextStrId();
            long low = Long.parseUnsignedLong(id.substring(16), 16);
            long seq = (low >>> SEQUENCE_SHIFT) & SEQUENCE_MASK;
            // 跨毫秒时序列号可能归零，seq <= prevSeq 说明跨了毫秒
            // 也可能在毫秒边界 sequence=0 前一次正好回卷后 sequence=0
            if (seq > prevSeq) {
                assertEquals(prevSeq + 1, seq, "同一毫秒内序列号应递增 1");
            } else if (seq == 0 && prevSeq >= 0) {
                // 回卷或跨毫秒归零，两种都正常
            }
            prevSeq = seq;
        }
    }

    @Test
    void timestampFieldIncreasesMonotonically() {
        Snowflake128Generator gen = new Snowflake128Generator(1, 0);
        long prevTs = -1;
        for (int i = 0; i < 1000; i++) {
            String id = gen.nextStrId();
            long high = Long.parseUnsignedLong(id.substring(0, 16), 16);
            long ts = (high >>> TIMESTAMP_SHIFT) & TIMESTAMP_MASK;
            assertTrue(ts >= prevTs, "时间戳字段应单调非递减，ts=" + ts + " prevTs=" + prevTs);
            prevTs = ts;
        }
    }

    @Test
    void genObjNumberIncreasesForEachInstance() {
        // 每个新实例的 genObjNumber 应递增（低 7 位）
        int instances = 10;
        Snowflake128Generator[] gens = new Snowflake128Generator[instances];
        Set<Long> objNumbers = new HashSet<>();
        for (int i = 0; i < instances; i++) {
            gens[i] = new Snowflake128Generator(1, 0);
            String id = gens[i].nextStrId();
            long low = Long.parseUnsignedLong(id.substring(16), 16);
            long objNum = (low >>> GEN_OBJ_NUMBER_SHIFT) & GEN_OBJ_NUMBER_MASK;
            assertTrue(objNumbers.add(objNum),
                    "各实例的 genObjNumber 应不同，重复值: " + objNum);
        }
    }

    @Test
    void backoffFieldsArePresent() {
        // 正常场景下回拨计数应为 0，回拨随机数存在但不校验具体值
        Snowflake128Generator gen = new Snowflake128Generator(1, 0);
        String id = gen.nextStrId();
        long low = Long.parseUnsignedLong(id.substring(16), 16);
        long backoffCount = (low >>> BACKOFF_COUNT_SHIFT) & BACKOFF_COUNT_MASK;
        long backoffRandom = (low >>> BACKOFF_RANDOM_SHIFT) & BACKOFF_RANDOM_MASK;
        assertEquals(0, backoffCount, "初始回拨计数应为 0");
        assertTrue(backoffRandom >= 0, "回拨随机数应为非负");
    }

    // ========================================================================
    // 不同参数 ID 可区分
    // ========================================================================

    @Test
    void differentMachineIdsProduceDistinctIds() {
        Snowflake128Generator gen1 = new Snowflake128Generator(1, 0);
        Snowflake128Generator gen2 = new Snowflake128Generator(2, 0);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(ids.add(gen1.nextStrId()));
            assertTrue(ids.add(gen2.nextStrId()));
        }
    }

    @Test
    void differentBizTypesProduceDistinctIds() {
        Snowflake128Generator gen1 = new Snowflake128Generator(1, 10);
        Snowflake128Generator gen2 = new Snowflake128Generator(1, 20);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(ids.add(gen1.nextStrId()));
            assertTrue(ids.add(gen2.nextStrId()));
        }
    }

    // ========================================================================
    // 并发测试
    // ========================================================================

    @RepeatedTest(3)
    void concurrentGenerationProducesUniqueIds() throws Exception {
        int threadCount = 4;
        int idsPerThread = 5_000;
        Snowflake128Generator gen = new Snowflake128Generator(1, 0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<String> allIds = ConcurrentHashMap.newKeySet();

        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                for (int i = 0; i < idsPerThread; i++) {
                    String id = gen.nextStrId();
                    assertTrue(allIds.add(id), "并发下产生重复 ID: " + id);
                }
                latch.countDown();
            });
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS), "并发测试超时");
        executor.shutdown();
        assertEquals(threadCount * idsPerThread, allIds.size());
    }

    // ========================================================================
    // 高容量唯一性
    // ========================================================================

    @Test
    void highVolumeUniqueness() {
        Snowflake128Generator gen = new Snowflake128Generator(1, 0);
        Set<String> ids = new HashSet<>();
        int count = 100_000;
        for (int i = 0; i < count; i++) {
            assertTrue(ids.add(gen.nextStrId()), "第 " + i + " 次生成重复 ID");
        }
        assertEquals(count, ids.size());
    }
}
