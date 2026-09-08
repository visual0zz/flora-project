package com.flora.root.crypto;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Argon2 按 lane 并行填充的回归测试。
 *
 * <p>期望值全部由并行化<em>之前</em>的串行实现生成（见
 * {@code addition/decision/decision20260908-04-flora-root.md}）。已有仓库的解锁与
 * KDBX 导入都依赖派生的 KEK，并行路径必须与串行路径逐字节一致，否则旧库将打不开。</p>
 *
 * <p>参数选取：需 {@code mPrime >= 4096} 且 {@code segmentLength >= 128} 才进入并行路径，
 * 故矩阵里同时保留了 p=1（lane 数不足，强制串行）与 p=64 且 segment 过小（回退串行）
 * 两个边界，确保两条路径都被覆盖。每种参数都跑 d/i/id 三型——数据无关（i）、
 * 数据相关（d）与混合（id）是三条独立分支；t≥2 用于覆盖 pass&gt;0 时引用上一 pass
 * 残留值的路径。</p>
 */
@Tag("slow")
class Argon2ParallelTest {

    private static final byte[] PWD = "password123".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SALT = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    /** 用例：{内存 KiB, 迭代, 并行度, 期望 hex(d), 期望 hex(i), 期望 hex(id)}。 */
    private static final Object[][] CASES = {
            // mPrime=4096 segmentLength=512 → 并行
            {4096, 1, 2,
                    "3e9119c9346cbf93108db6c66ad9c87842900f0bef62ed612fa976694f9a560a",
                    "14b7fe9ccb0e5969063d3b540a5c66f25fcb6fd2b29b9159fc5f14dbbc47be55",
                    "31a059aa591eef1e0b5970134a1739139f8c4671326f4cf5fdefbbeda3fb5512"},
            // segmentLength=512 → 并行；t=2 覆盖 pass>0
            {8192, 2, 4,
                    "08d6cd77044fd1e3a523c6f6912847e172698eed75e401ea0437629eb68b859a",
                    "3c7b394a247a74830803391fa734a9b7cbc33a6f33f11518ebcb39db8474a932",
                    "6b409357fac0dcae4530baa38edf54d2cfc9a598a897276a74594282d6d02d97"},
            {16384, 1, 8,
                    "66efaa5cb450e12ba78d79979467d8bd0dad0197d500795ef089153c6a83289a",
                    "62e3a55b573f6221c4b08aed6c912dc61dca1e10b4729926656910549f48f39a",
                    "b32c4a697d64cf831725190ac1de5591669b6f8cb870b9fb62349054e0ea1d25"},
            // segmentLength=4096 → 并行
            {65536, 1, 4,
                    "38d5b1626d84d38d202014d7b70a63ad539c77aeff3ffa83c136ad3916e2ae6c",
                    "7984e3e9e978aba281b6c70e4ec14a330b66e961f09dac167991e1294713d478",
                    "937239007eeb0def28038cafcf2d6de239ffeb40d68fc0cd08ba18bb2325580d"},
            // p=1：lane 数不足，强制串行
            {8192, 1, 1,
                    "a81d8642f9ab85e60bd9f5a1990b5f9ce0fe314235ece0682e81fbaac0c0f352",
                    "c6c3ffdaf45b32d133c29f60a1b23d6e55a543daf0508424ed3ab19f8484df46",
                    "3b1a1ed6c75b4e5bdfc4ac32ef70809ce44fa208f76239589a9aee8ca8124df0"},
            // p=64 但 segmentLength=32 < 128：任务过碎，回退串行
            {8192, 3, 64,
                    "25922176427beed9572ce44187d2cdf872520cde3044cd15c384f3db0f7403e1",
                    "63166e9861eb78211b4577dc0af950f3a64bfcfdf0dbcf1a6f653d1dce69bb72",
                    "bf9658d8dffdf5c0cc93bdd301153c067e453276f344ccb397bc222cc0e13143"},
            // segmentLength=256 → 并行；t=2 + p=64
            {65536, 2, 64,
                    "c93a3c3c8195117f98915f6fd416c06480c4c3d6f94074e05967bfceffc411f4",
                    "b6e3d3717a20d4353d1d10733feefb104208ccc3ceddd2d4a103d153c501f773",
                    "6c14c8ecf39966c598f7a0603b47568202bdf189d271e1e434429f21b220903a"},
    };

    /** 并行/串行两条路径的输出都必须等于并行化前的串行 golden 值。 */
    @Test
    void matchesPreParallelGoldenValues() {
        List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
        for (Object[] c : CASES) {
            int m = (Integer) c[0];
            int t = (Integer) c[1];
            int p = (Integer) c[2];
            int[] types = {Argon2.TYPE_D, Argon2.TYPE_I, Argon2.TYPE_ID};
            String[] names = {"d", "i", "id"};
            for (int k = 0; k < types.length; k++) {
                String expected = (String) c[3 + k];
                int type = types[k];
                String name = names[k];
                checks.add(() -> assertEquals(expected, hex(Argon2.digest(type, PWD, SALT, m, t, p, 32)),
                        "m=" + m + " t=" + t + " p=" + p + " type=" + name));
            }
        }
        assertAll(checks);
    }

    /**
     * 并发调用同一组参数必须得到同样的结果：验证 slice 之间的 happens-before
     * （fork/join 屏障）确实生效，不存在读到其它线程半成品块的情况。
     * 用 assertTimeoutPreemptively 兜底，一旦屏障写错导致死锁会在超时处失败而不是挂住整个套件。
     */
    @Test
    void concurrentDigestsAreStable() throws Exception {
        final int m = 4096;
        final int t = 1;
        final int p = 2;
        final String expected = "31a059aa591eef1e0b5970134a1739139f8c4671326f4cf5fdefbbeda3fb5512";
        assertTimeoutPreemptively(Duration.ofSeconds(120), () -> {
            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                CountDownLatch start = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(threads);
                AtomicReference<String> failure = new AtomicReference<>();
                for (int i = 0; i < threads; i++) {
                    pool.execute(() -> {
                        try {
                            start.await();
                            for (int n = 0; n < 10; n++) {
                                String actual = hex(Argon2.digest(PWD, SALT, m, t, p, 32));
                                if (!expected.equals(actual)) {
                                    failure.compareAndSet(null, actual);
                                    return;
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                start.countDown();
                if (!done.await(110, TimeUnit.SECONDS)) {
                    throw new AssertionError("并发派生未在超时前完成（疑似死锁）");
                }
                assertNull(failure.get(), "并发派生结果与串行 golden 不一致，实际=" + failure.get());
            } finally {
                pool.shutdownNow();
            }
        });
    }

    /**
     * 在 ForkJoinPool 工作线程内部嵌套调用（调用方本身已在并行流里）不得死锁，且结果一致。
     * 验证并行填充没有依赖「调用方是外部线程」这一前提。
     */
    @Test
    void nestedCallInsideForkJoinWorkerSucceeds() {
        final String expected = "31a059aa591eef1e0b5970134a1739139f8c4671326f4cf5fdefbbeda3fb5512";
        assertTimeoutPreemptively(Duration.ofSeconds(120), () -> {
            AtomicReference<String> failure = new AtomicReference<>();
            IntStream.range(0, 8).parallel().forEach(i -> {
                String actual = hex(Argon2.digest(PWD, SALT, 4096, 1, 2, 32));
                if (!expected.equals(actual)) {
                    failure.compareAndSet(null, actual);
                }
            });
            assertNull(failure.get(), "嵌套调用结果与串行 golden 不一致，实际=" + failure.get());
        });
    }

    private static String hex(byte[] out) {
        return HexFormat.of().formatHex(out);
    }
}
