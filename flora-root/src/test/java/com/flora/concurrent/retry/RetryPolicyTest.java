package com.flora.concurrent.retry;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重试策略的单元测试。
 * 测试默认值、次数与退避设置、可重试异常判定及参数校验。
 */
class RetryPolicyTest {

    @Test
    void defaults() {
        RetryPolicy p = RetryPolicy.builder().build();
        assertEquals(3, p.maxAttempts());
        assertTrue(p.shouldRetry(new RuntimeException()));
        assertFalse(p.shouldRetry(new Error()));
    }

    @Test
    void customMaxAttempts() {
        RetryPolicy p = RetryPolicy.builder().maxAttempts(5).build();
        assertEquals(5, p.maxAttempts());
    }

    @Test
    void invalidMaxAttemptsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder().maxAttempts(0));
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.builder().maxAttempts(-1));
    }

    @Test
    void retryOnReplacesDefault() {
        RetryPolicy p = RetryPolicy.builder().retryOn(IOException.class).build();
        assertTrue(p.shouldRetry(new IOException()));
        assertFalse(p.shouldRetry(new RuntimeException()));
        assertFalse(p.shouldRetry(new Error()));
    }

    @Test
    void retryOnMultipleTypesAccumulates() {
        RetryPolicy p = RetryPolicy.builder()
                .retryOn(IOException.class)
                .retryOn(IllegalStateException.class)
                .build();
        assertTrue(p.shouldRetry(new IOException()));
        assertTrue(p.shouldRetry(new IllegalStateException()));
        assertFalse(p.shouldRetry(new IllegalArgumentException()));
    }

    @Test
    void retryOnPredicateAccumulates() {
        RetryPolicy p = RetryPolicy.builder()
                .retryOn(IOException.class)
                .retryOn(t -> t instanceof IllegalStateException)
                .build();
        assertTrue(p.shouldRetry(new IOException()));
        assertTrue(p.shouldRetry(new IllegalStateException()));
        assertFalse(p.shouldRetry(new IllegalArgumentException()));
    }

    @Test
    void retryOnSubclassMatches() {
        RetryPolicy p = RetryPolicy.builder().retryOn(IOException.class).build();
        assertTrue(p.shouldRetry(new java.io.FileNotFoundException())); // IOException 子类
    }

    @Test
    void backoffSetter() {
        Backoff b = Backoff.fixed(Duration.ofSeconds(1));
        RetryPolicy p = RetryPolicy.builder().backoff(b).build();
        assertSame(b, p.backoff());
    }

    @Test
    void nullBackoffFallsBackToImmediate() {
        RetryPolicy p = RetryPolicy.builder().backoff(null).build();
        assertEquals(0, p.backoff().delayNanos(1));
    }
}
