package com.flora.root.concurrent.retry;

import java.util.Objects;

/**
 * 重试耗尽异常:所有尝试均失败且均判定为可重试时由 {@link Retryer} 抛出。
 * <p>
 * {@link #lastFailure()} 提供最后一次尝试抛出的异常,可作为失败根因。
 */
public final class RetryExhaustedException extends RuntimeException {

    private final int attempts;
    private final Throwable lastFailure;

    /**
     * 构造异常。
     *
     * @param attempts    已执行的尝试总次数
     * @param lastFailure 最后一次尝试抛出的异常
     */
    public RetryExhaustedException(int attempts, Throwable lastFailure) {
        super("重试耗尽:共尝试 " + attempts + " 次仍失败", lastFailure);
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts 必须大于等于 1: " + attempts);
        }
        this.attempts = attempts;
        this.lastFailure = Objects.requireNonNull(lastFailure, "lastFailure");
    }

    /** 已执行的尝试总次数。 */
    public int attempts() {
        return attempts;
    }

    /** 最后一次尝试抛出的异常。 */
    public Throwable lastFailure() {
        return lastFailure;
    }
}
