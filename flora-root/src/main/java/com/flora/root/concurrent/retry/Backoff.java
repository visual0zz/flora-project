package com.flora.root.concurrent.retry;

import java.time.Duration;
import java.util.Objects;

/**
 * 重试退避策略:决定第 N 次重试前应等待的时长。
 * <p>
 * {@code attempt} 从 1 开始计数,即 {@link #delayNanos(int) delayNanos(1)} 是首次重试前(第一次尝试失败后)的等待时长。
 * 返回非正数表示不等待、立即重试。实现应无状态,以便被多个 {@link Retryer} 共享。
 */
@FunctionalInterface
public interface Backoff {

    /**
     * 计算第 {@code attempt} 次重试前的等待时长。
     *
     * @param attempt 重试序号,从 1 开始
     * @return 等待时长,单位为纳秒;非正数表示不等待
     */
    long delayNanos(int attempt);

    /**
     * 固定间隔退避:每次重试前等待相同的时长。
     *
     * @param delay 固定等待时长,不能为负
     */
    static Backoff fixed(Duration delay) {
        requireNonNegative(delay, "delay");
        long nanos = delay.toNanos();
        return attempt -> nanos;
    }

    /**
     * 线性递增退避:第 N 次重试前等待 {@code initialDelay + increment * (N - 1)}。
     *
     * @param initialDelay 首次重试前的等待时长,不能为负
     * @param increment    每次重试递增的等待时长,不能为负
     */
    static Backoff linear(Duration initialDelay, Duration increment) {
        requireNonNegative(initialDelay, "initialDelay");
        requireNonNegative(increment, "increment");
        long base = initialDelay.toNanos();
        long step = increment.toNanos();
        return attempt -> {
            long value = base + step * (long) (attempt - 1);
            return Math.max(value, 0);
        };
    }

    /**
     * 指数退避:第 N 次重试前等待 {@code min(initialDelay * multiplier^(N - 1), maxDelay)}。
     *
     * @param initialDelay 首次重试前的等待时长,必须为正
     * @param multiplier   退避增长因子,必须是不小于 1 的有限值
     * @param maxDelay     等待时长的上限,必须不小于 {@code initialDelay}
     */
    static Backoff exponential(Duration initialDelay, double multiplier, Duration maxDelay) {
        requireNonNegative(initialDelay, "initialDelay");
        if (initialDelay.isZero()) {
            throw new IllegalArgumentException("initialDelay 必须为正: " + initialDelay);
        }
        if (!Double.isFinite(multiplier) || multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier 必须是不小于 1 的有限值: " + multiplier);
        }
        Objects.requireNonNull(maxDelay, "maxDelay");
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay 不能小于 initialDelay: " + maxDelay);
        }
        long base = initialDelay.toNanos();
        long cap = maxDelay.toNanos();
        return attempt -> {
            double value = base * Math.pow(multiplier, attempt - 1);
            if (value >= cap) {
                return cap;
            }
            return (long) value;
        };
    }

    /** 校验时长为非负。 */
    private static void requireNonNegative(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " 不能为负: " + duration);
        }
    }
}
