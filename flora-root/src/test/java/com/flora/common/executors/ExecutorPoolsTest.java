package com.flora.common.executors;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorPoolsTest {

    private static final int CPUS = Runtime.getRuntime().availableProcessors();

    @Test
    void executorReturnsProcessWideSingleton() {
        assertSame(ExecutorPools.executor(TaskKind.COMPUTE), ExecutorPools.executor(TaskKind.COMPUTE));
        assertSame(ExecutorPools.executor(TaskKind.IO), ExecutorPools.executor(TaskKind.IO));
        assertSame(ExecutorPools.scheduled(), ExecutorPools.scheduled());
    }

    @Test
    void threadsAreDaemonWithKindPrefix() throws Exception {
        AtomicReference<String> name = new AtomicReference<>();
        AtomicBoolean daemon = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);
        ExecutorPools.executor(TaskKind.COMPUTE).execute(() -> {
            name.set(Thread.currentThread().getName());
            daemon.set(Thread.currentThread().isDaemon());
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS), "任务应被执行");
        assertTrue(name.get().startsWith("flora-compute-"), "线程名应带类别前缀: " + name.get());
        assertTrue(daemon.get(), "共享线程应为守护线程");
    }

    @Test
    void computePoolDoesNotDropTasksUnderPressure() throws Exception {
        // COMPUTE 用 CallerRunsPolicy：队列溢出时由提交线程兜底执行，任务不应丢失。
        int submitCount = CPUS + Math.max(256, CPUS * 64) + 200;
        CountDownLatch allDone = new CountDownLatch(submitCount);
        AtomicInteger executed = new AtomicInteger();
        AtomicBoolean ranInCaller = new AtomicBoolean();
        Thread caller = Thread.currentThread();
        Executor compute = ExecutorPools.executor(TaskKind.COMPUTE);
        for (int i = 0; i < submitCount; i++) {
            compute.execute(() -> {
                executed.incrementAndGet();
                if (Thread.currentThread() == caller) ranInCaller.set(true);
                allDone.countDown();
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ignored) {
                }
            });
        }
        assertTrue(allDone.await(15, TimeUnit.SECONDS), "所有任务应被执行（含调用线程兜底）");
        assertEquals(submitCount, executed.get(), "COMPUTE 池不应丢弃任务");
        assertTrue(ranInCaller.get(), "溢出时应有任务在调用线程执行（CallerRuns）");
    }

    @Test
    void ioPoolDiscardsExcessWhenSaturated() throws Exception {
        // IO 用 DiscardPolicy：线程数 + 队列容量之外的任务被静默丢弃。
        int maxThreads = CPUS * 2;
        int queueCap = 1024;
        int submitCount = maxThreads + queueCap + 500;
        CountDownLatch hold = new CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();
        Executor io = ExecutorPools.executor(TaskKind.IO);
        for (int i = 0; i < submitCount; i++) {
            io.execute(() -> {
                executed.incrementAndGet();
                try {
                    hold.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            });
        }
        Thread.sleep(400); // 让线程调度与拒绝发生
        hold.countDown();
        assertTrue(executed.get() < submitCount,
                "IO 池饱和时应丢弃部分超额任务, executed=" + executed.get() + " submitted=" + submitCount);
    }

    @Test
    void taskExceptionIsNotPropagatedToSubmitter() throws Exception {
        // 提交线程不应因任务异常而收到未捕获异常；异常由 afterExecute 记录。
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorPools.executor(TaskKind.COMPUTE).execute(() -> {
            latch.countDown();
            throw new RuntimeException("boom");
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(100); // 等待 afterExecute 处理，期间不抛出即视为通过
    }
}
