package com.flora.root.concurrent.retry;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 重试策略:定义总尝试次数、退避策略与可重试异常判定。
 * <p>
 * 通过 {@link #builder()} 构建。构建出的实例不可变,可被多个 {@link Retryer} 共享。
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final Backoff backoff;
    private final Predicate<Throwable> retryable;

    private RetryPolicy(int maxAttempts, Backoff backoff, Predicate<Throwable> retryable) {
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
        this.retryable = retryable;
    }

    /** 总尝试次数,即首次执行加上最多 {@code maxAttempts - 1} 次重试。 */
    public int maxAttempts() {
        return maxAttempts;
    }

    /** 退避策略,决定每次重试前的等待时长。 */
    public Backoff backoff() {
        return backoff;
    }

    /**
     * 判断指定异常是否应触发重试。
     *
     * @param t 最近一次尝试抛出的异常
     * @return {@code true} 表示可重试
     */
    public boolean shouldRetry(Throwable t) {
        return retryable.test(t);
    }

    /** 创建策略构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 重试策略构建器。
     * <p>
     * 默认配置:最多尝试 3 次、失败后立即重试、重试所有 {@link Exception}(不包括 {@link Error})。
     */
    public static final class Builder {

        private int maxAttempts = 3;
        private Backoff backoff = Backoff.fixed(Duration.ZERO);
        private Predicate<Throwable> retryable;

        private Builder() {
        }

        /**
         * 设置总尝试次数,必须大于等于 1。
         */
        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts 必须大于等于 1: " + maxAttempts);
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * 设置退避策略;传 {@code null} 时回退为失败后立即重试。
         */
        public Builder backoff(Backoff backoff) {
            this.backoff = backoff != null ? backoff : Backoff.fixed(Duration.ZERO);
            return this;
        }

        /**
         * 追加一个可重试的异常类型,可多次调用以累计多个类型。
         * <p>
         * 一旦显式调用本方法或 {@link #retryOn(Predicate)},默认的"重试所有异常"即被替换,
         * 仅显式声明的异常能触发重试。
         */
        public Builder retryOn(Class<? extends Throwable> type) {
            Objects.requireNonNull(type, "type");
            return retryOn(type::isInstance);
        }

        /**
         * 追加一个可重试异常判定谓词,可多次调用,满足任一谓词即重试。
         * <p>
         * 一旦显式调用本方法或 {@link #retryOn(Class)},默认的"重试所有异常"即被替换,
         * 仅显式声明的异常能触发重试。
         */
        public Builder retryOn(Predicate<Throwable> predicate) {
            Objects.requireNonNull(predicate, "predicate");
            retryable = retryable == null ? predicate : retryable.or(predicate);
            return this;
        }

        /** 构建策略实例。 */
        public RetryPolicy build() {
            Predicate<Throwable> effective =
                    retryable != null ? retryable : (Throwable t) -> t instanceof Exception;
            return new RetryPolicy(maxAttempts, backoff, effective);
        }
    }
}
