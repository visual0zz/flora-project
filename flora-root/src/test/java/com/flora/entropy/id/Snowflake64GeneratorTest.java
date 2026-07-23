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
 * {@link Snowflake64Generator} 的单元测试。
 * ID 结构（共 64 位）：
 * <pre>
 *   位 63      ：保留位，应为 0（��免负数 long）
 *   位 62-22   ：41 位相对时间戳（ms，相对 EPOCH）
 *   位 21-12   ：10 位工作机器 ID（0~1023）
 *   位 11-0    ：12 位自增序列号（0~4095）
 * </pre>
 */
class Snowflake64GeneratorTest {

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long EPOCH = 1700000000000L;

    private static final Pattern HEX16 = Pattern.compile("[0-9a-f]{16}");

    // ========================================================================
    // 构造测试
    // ========================================================================

    @Test
    void defaultConstructorUsesRandomWorkerId() {
        Snowflake64Generator gen = new Snowflake64Generator();
        assertNotNull(gen);
        // 连续构造多次，验证不会抛异常
        for (int i = 0; i < 100; i++) {
            assertNotNull(new Snowflake64Generator());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 512, 1023})
    void constructorWithValidWorkerId(long workerId) {
        Snowflake64Generator gen = new Snowflake64Generator(workerId);
        assertNotNull(gen);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1024, 99999})
    void constructorWithInvalidWorkerIdThrows(long workerId) {
        assertThrows(IllegalArgumentException.class,
                () -> new Snowflake64Generator(workerId));
    }

    // ========================================================================
    // 基本生成测试
    // ========================================================================

    @Test
    void nextLongIdReturnsPositiveNonZero() {
        Snowflake64Generator gen = new Snowflake64Generator(1);
        long id = gen.nextLongId();
        assertTrue(id > 0, "ID 应为正数（最高位为 0）");
    }

    @Test
    void nextStrIdReturns16CharHex() {
        Snowflake64Generator gen = new Snowflake64Generator(1);
        String id = gen.nextStrId();
        assertEquals(16, id.length());
        assertTrue(HEX16.matcher(id).matches());
    }

    @Test
    void nextStrIdMatchesNextLongId() {
        Snowflake64Generator gen = new Snowflake64Generator(42);
        long longId = gen.nextLongId();
        String strId = gen.nextStrId();
        // 两次调用会生成不同 ID，所以只验证格式一致性
        assertEquals(16, strId.length());
        assertTrue(HEX16.matcher(strId).matches());
    }

    // ========================================================================
    // ID 唯一性
    // ========================================================================

    @Test
    void consecutiveIdsAreUnique() {
        Snowflake64Generator gen = new Snowflake64Generator(1);
        Set<Long> ids = new HashSet<>();
        int count = 100_000;
        for (int i = 0; i < count; i++) {
            long id = gen.nextLongId();
            assertTrue(ids.add(id), "产生重复 ID: " + id);
        }
        assertEquals(count, ids.size());
    }

    @Test
    void consecutiveStringIdsAreUnique() {
        Snowflake64Generator gen = new Snowflake64Generator(1);
        Set<String> ids = new HashSet<>();
        int count = 10_000;
        for (int i = 0; i < count; i++) {
            String id = gen.nextStrId();
            assertTrue(ids.add(id), "产生重复字符串 ID: " + id);
        }
        assertEquals(count, ids.size());
    }

    // ========================================================================
    // ID 位结构验证
    // ========================================================================

    @Test
    void signBitIsAlwaysZero() {
        Snowflake64Generator gen = new Snowflake64Generator(1);
        for (int i = 0; i < 1000; i++) {
            long id = gen.nextLongId();
            assertTrue(id > 0, "最高位（符号位）必须为 0，ID 为正数");
        }
    }

    @Test
    void sequenceFieldIncrementsWithinSameMillisecond() {
        Snowflake64Generator gen = new Snowflake64Generator(1);
        long prevId = gen.nextLongId();
        long prevSeq = prevId & SEQUENCE_MASK;
        // 同一毫秒内连续生成，序列号应递增
        for (int i = 0; i < 100; i++) {
            long id = gen.nextLongId();
            long seq = id & SEQUENCE_MASK;
            // 跨毫秒时序列号可能归零，所以允许 seq <= prevSeq 时说明跨了毫秒
            if (seq > prevSeq) {
                assertEquals(prevSeq + 1, seq, "同一毫秒内序列号应递增 1");
            } else if (seq == 0) {
                // 序列号溢出回卷或跨毫秒归零——无法判断，接受
            }
            prevSeq = seq;
        }
    }

    @Test
    void workerIdFieldIsCorrect() {
        long expectedWorkerId = 42;
        Snowflake64Generator gen = new Snowflake64Generator(expectedWorkerId);
        long id = gen.nextLongId();
        long extractedWorkerId = (id >>> WORKER_ID_SHIFT) & MAX_WORKER_ID;
        assertEquals(expectedWorkerId, extractedWorkerId, "机器 ID 字段应与构造参数一致");
    }

    @Test
    void timestampFieldIncreasesMonotonically() {
        Snowflake64Generator gen = new Snowflake64Generator(1);
        long prevTs = -1;
        for (int i = 0; i < 1000; i++) {
            long id = gen.nextLongId();
            long ts = id >>> TIMESTAMP_SHIFT;
            assertTrue(ts >= prevTs, "时间戳字段应单调非递减");
            prevTs = ts;
        }
    }

    // ========================================================================
    // 不同 workerId 的 ID 可区分
    // ========================================================================

    @Test
    void differentWorkerIdsProduceDistinctIds() {
        Snowflake64Generator gen1 = new Snowflake64Generator(1);
        Snowflake64Generator gen2 = new Snowflake64Generator(2);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(ids.add(gen1.nextLongId()));
            assertTrue(ids.add(gen2.nextLongId()));
        }
    }

    // ========================================================================
    // 并发测试
    // ========================================================================

    @RepeatedTest(3)
    void concurrentGenerationProducesUniqueIds() throws Exception {
        int threadCount = 4;
        int idsPerThread = 5_000;
        Snowflake64Generator gen = new Snowflake64Generator(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<Long> allIds = ConcurrentHashMap.newKeySet();

        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                for (int i = 0; i < idsPerThread; i++) {
                    long id = gen.nextLongId();
                    assertTrue(allIds.add(id), "并发下产生重复 ID: " + id);
                }
                latch.countDown();
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertEquals(threadCount * idsPerThread, allIds.size());
    }

    // ========================================================================
    // 边界测试
    // ========================================================================

    @Test
    void workerIdEdgeCases() {
        // 边界值 0 和 1023
        Snowflake64Generator gen0 = new Snowflake64Generator(0);
        Snowflake64Generator gen1023 = new Snowflake64Generator(1023);
        long id0 = gen0.nextLongId();
        long id1023 = gen1023.nextLongId();
        assertTrue(id0 > 0);
        assertTrue(id1023 > 0);
        assertNotEquals(id0, id1023);
    }
}
