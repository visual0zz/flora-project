package com.flora.root.concurrent.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重试退避策略的单元测试。
 * 测试 fixed / linear / exponential 三种退避的时长计算与参数校验。
 */
class BackoffTest {

    @Test
    void fixedDelayConstant() {
        Backoff b = Backoff.fixed(Duration.ofMillis(100));
        assertEquals(100_000_000L, b.delayNanos(1));
        assertEquals(100_000_000L, b.delayNanos(9));
    }

    @Test
    void fixedZeroAllowed() {
        Backoff b = Backoff.fixed(Duration.ZERO);
        assertEquals(0, b.delayNanos(1));
    }

    @Test
    void fixedNegativeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Backoff.fixed(Duration.ofMillis(-1)));
    }

    @Test
    void fixedNullRejected() {
        assertThrows(NullPointerException.class,
                () -> Backoff.fixed(null));
    }

    @Test
    void linearIncreasesByIncrement() {
        Backoff b = Backoff.linear(Duration.ofMillis(10), Duration.ofMillis(20));
        assertEquals(10_000_000L, b.delayNanos(1));
        assertEquals(30_000_000L, b.delayNanos(2));
        assertEquals(50_000_000L, b.delayNanos(3));
    }

    @Test
    void linearNegativeInitialRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Backoff.linear(Duration.ofMillis(-10), Duration.ofMillis(20)));
    }

    @Test
    void exponentialGrowthCappedByMax() {
        Backoff b = Backoff.exponential(Duration.ofMillis(100), 2.0, Duration.ofSeconds(1));
        assertEquals(100_000_000L, b.delayNanos(1));
        assertEquals(200_000_000L, b.delayNanos(2));
        assertEquals(400_000_000L, b.delayNanos(3));
        assertEquals(1_000_000_000L, b.delayNanos(10)); // 到达上限
        assertEquals(1_000_000_000L, b.delayNanos(100));
    }

    @Test
    void exponentialZeroInitialRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Backoff.exponential(Duration.ZERO, 2.0, Duration.ofSeconds(1)));
    }

    @Test
    void exponentialInvalidMultiplierRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Backoff.exponential(Duration.ofMillis(100), 0.5, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> Backoff.exponential(Duration.ofMillis(100), Double.NaN, Duration.ofSeconds(1)));
    }

    @Test
    void exponentialMaxBelowInitialRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Backoff.exponential(Duration.ofMillis(100), 2.0, Duration.ofMillis(50)));
    }

    @Test
    void delaysAreNonNegative() {
        Backoff linear = Backoff.linear(Duration.ZERO, Duration.ZERO);
        assertTrue(linear.delayNanos(100) >= 0);
    }
}
