package com.flora.common.executors;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutorPoolsTest {

    private static final int CPUS = Runtime.getRuntime().availableProcessors();

    @Test
    void computeRunsOnCommonPool() throws Exception {
        AtomicReference<String> name = new AtomicReference<>();
        AtomicBoolean daemon = new AtomicBoolean();
        CountDownLatch done = new CountDownLatch(1);
        InternalExecutors.compute(() -> {
            name.set(Thread.currentThread().getName());
            daemon.set(Thread.currentThread().isDaemon());
            done.countDown();
        });
        assertTrue(done.await(5, TimeUnit.SECONDS), "任务应被执行");
        assertTrue(name.get().startsWith("ForkJoinPool.commonPool"),
                "COMPUTE 应跑在 ForkJoinPool.commonPool: " + name.get());
        assertTrue(daemon.get(), "commonPool 线程应为守护线程");
    }

    @Test
    void computeDoesNotDropTasks() throws Exception {
        // commonPool 为无界工作窃取池，所有提交的任务都应被执行。
        int submitCount = CPUS * 4 + 200;
        CountDownLatch allDone = new CountDownLatch(submitCount);
        AtomicInteger executed = new AtomicInteger();
        for (int i = 0; i < submitCount; i++) {
            InternalExecutors.compute(() -> {
                executed.incrementAndGet();
                allDone.countDown();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            });
        }
        assertTrue(allDone.await(15, TimeUnit.SECONDS), "所有任务应被执行");
        assertEquals(submitCount, executed.get(), "COMPUTE 任务不应丢失");
    }

    @Test
    void ioPoolDiscardsExcessWhenSaturated() throws Exception {
        // IO 用 DiscardPolicy：线程数 + 队列容量之外的任务被静默丢弃。
        int maxThreads = CPUS * 2;
        int queueCap = 1024;
        int submitCount = maxThreads + queueCap + 500;
        CountDownLatch hold = new CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();
        for (int i = 0; i < submitCount; i++) {
            InternalExecutors.io(() -> {
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
        InternalExecutors.compute(() -> {
            latch.countDown();
            throw new RuntimeException("boom");
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        Thread.sleep(100); // 等待 afterExecute 处理，期间不抛出即视为通过
    }

    @Test
    void scheduleRunsAfterDelay() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        long start = System.nanoTime();
        InternalExecutors.schedule(() -> done.countDown(), 200, TimeUnit.MILLISECONDS);
        assertTrue(done.await(2, TimeUnit.SECONDS), "延迟任务应被执行");
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMillis >= 180, "任务应在延迟后执行, elapsed=" + elapsedMillis);
    }

    @Test
    void refreshDelegatesToIoPool() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        InternalExecutors.refresh(done::countDown);
        assertTrue(done.await(2, TimeUnit.SECONDS), "refresh 任务应被执行");
    }

    @Test
    void lightRunsTask() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        InternalExecutors.light(done::countDown);
        assertTrue(done.await(2, TimeUnit.SECONDS), "light 任务应被执行");
    }
}