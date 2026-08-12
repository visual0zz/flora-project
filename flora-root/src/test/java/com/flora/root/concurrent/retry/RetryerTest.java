package com.flora.root.concurrent.retry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重试执行器的单元测试。
 * 测试成功路径、重试耗尽、不可重试异常直抛、退避等待与中断处理。
 */
class RetryerTest {

    @Test
    void firstAttemptSucceeds() throws Exception {
        Retryer retryer = Retryer.of(RetryPolicy.builder().maxAttempts(3).build());
        String result = retryer.call(() -> "ok");
        assertEquals("ok", result);
    }

    @Test
    void retriesThenSucceeds() throws Exception {
        AtomicInteger count = new AtomicInteger();
        Retryer retryer = Retryer.of(RetryPolicy.builder().maxAttempts(3).build());
        String result = retryer.call(() -> {
            if (count.incrementAndGet() < 3) {
                throw new IOException("boom");
            }
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(3, count.get());
    }

    @Test
    void exhaustedThrowsRetryExhausted() {
        AtomicInteger count = new AtomicInteger();
        Retryer retryer = Retryer.of(RetryPolicy.builder().maxAttempts(3).build());
        RetryExhaustedException ex = assertThrows(RetryExhaustedException.class,
                () -> retryer.call(() -> {
                    count.incrementAndGet();
                    throw new IOException("boom");
                }));
        assertEquals(3, count.get());
        assertEquals(3, ex.attempts());
        assertInstanceOf(IOException.class, ex.lastFailure());
    }

    @Test
    void nonRetryableThrowsImmediately() {
        AtomicInteger count = new AtomicInteger();
        Retryer retryer = Retryer.of(RetryPolicy.builder()
                .maxAttempts(3)
                .retryOn(IOException.class)
                .build());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> retryer.call(() -> {
                    count.incrementAndGet();
                    throw new IllegalStateException("no retry");
                }));
        assertEquals("no retry", ex.getMessage());
        assertEquals(1, count.get());
    }

    @Test
    void errorIsNotRetriedByDefault() {
        AtomicInteger count = new AtomicInteger();
        Retryer retryer = Retryer.of(RetryPolicy.builder().maxAttempts(3).build());
        assertThrows(AssertionError.class, () -> retryer.call(() -> {
            count.incrementAndGet();
            throw new AssertionError("fatal");
        }));
        assertEquals(1, count.get());
    }

    @Test
    void onlyRetryOnDeclaredType() {
        AtomicInteger count = new AtomicInteger();
        Retryer retryer = Retryer.of(RetryPolicy.builder()
                .maxAttempts(5)
                .retryOn(IOException.class)
                .build());
        RetryExhaustedException ex = assertThrows(RetryExhaustedException.class,
                () -> retryer.call(() -> {
                    count.incrementAndGet();
                    throw new IOException("x");
                }));
        assertEquals(5, ex.attempts());
        assertEquals(5, count.get());
    }

    @Test
    void runExecutesVoidTask() throws Exception {
        AtomicInteger count = new AtomicInteger();
        Retryer retryer = Retryer.of(RetryPolicy.builder().maxAttempts(2).build());
        retryer.run(() -> {
            if (count.incrementAndGet() < 2) {
                throw new IOException("retry me");
            }
        });
        assertEquals(2, count.get());
    }

    @Test
    void backoffDelayRespected() {
        long start = System.nanoTime();
        Retryer retryer = Retryer.of(RetryPolicy.builder()
                .maxAttempts(3)
                .backoff(Backoff.fixed(Duration.ofMillis(80)))
                .build());
        assertThrows(RetryExhaustedException.class,
                () -> retryer.call(() -> {
                    throw new IOException("x");
                }));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // 两次重试间隔各 80ms,预留宽松余量
        assertTrue(elapsedMs >= 150, "elapsedMs=" + elapsedMs);
    }

    @Test
    void nullTaskRejected() {
        Retryer retryer = Retryer.of(RetryPolicy.builder().build());
        assertThrows(NullPointerException.class, () -> retryer.call(null));
        assertThrows(NullPointerException.class, () -> retryer.run(null));
    }

    @Test
    void nullPolicyRejected() {
        assertThrows(NullPointerException.class, () -> Retryer.of(null));
    }

    @Test
    void interruptDuringBackoffStopsRetrying() throws Exception {
        Retryer retryer = Retryer.of(RetryPolicy.builder()
                .maxAttempts(10)
                .backoff(Backoff.fixed(Duration.ofSeconds(10)))
                .build());
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                retryer.run(() -> {
                    throw new IOException("always fail");
                });
            } catch (Throwable t) {
                caught.set(t);
            }
        });
        worker.start();
        while (worker.getState() == Thread.State.NEW) {
            Thread.onSpinWait();
        }
        Thread.sleep(100); // 确保已进入退避等待
        worker.interrupt();
        worker.join(5_000);
        assertFalse(worker.isAlive(), "worker 未在中断后退出");
        assertInstanceOf(InterruptedException.class, caught.get());
    }

    @Test
    void policyExposed() {
        RetryPolicy policy = RetryPolicy.builder().maxAttempts(4).build();
        Retryer retryer = Retryer.of(policy);
        assertEquals(4, retryer.policy().maxAttempts());
    }
}
