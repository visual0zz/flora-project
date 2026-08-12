package com.flora.root.concurrent.retry;

import com.flora.root.tag.ModuleEntry;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 重试执行器:按 {@link RetryPolicy} 反复执行任务直至成功、耗尽次数或遇到不可重试异常。
 * <p>
 * 实例不可变且线程安全,可被多个线程共享。等待退避期间响应线程中断,中断会中止重试并恢复中断状态。
 */
@ModuleEntry
public final class Retryer {

    private final RetryPolicy policy;

    private Retryer(RetryPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * 基于指定策略创建执行器。
     *
     * @param policy 重试策略,不能为 {@code null}
     */
    public static Retryer of(RetryPolicy policy) {
        return new Retryer(policy);
    }

    /** 返回执行器使用的策略。 */
    public RetryPolicy policy() {
        return policy;
    }

    /**
     * 执行任务直至成功或终止。
     *
     * @param task 要执行的任务,不能为 {@code null}
     * @return 首次成功尝试的返回值
     * @throws RetryExhaustedException 所有尝试均失败且均判定为可重试时抛出
     * @throws InterruptedException    等待退避期间线程被中断时抛出(同时恢复中断状态)
     */
    public <T> T call(Callable<T> task) throws Exception {
        Objects.requireNonNull(task, "task");
        int maxAttempts = policy.maxAttempts();
        Backoff backoff = policy.backoff();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return task.call();
            } catch (Throwable t) {
                if (!policy.shouldRetry(t)) {
                    throw sneakyThrow(t); // 不可重试,原样抛出
                }
                if (attempt >= maxAttempts) {
                    throw new RetryExhaustedException(attempt, t);
                }
                sleep(backoff.delayNanos(attempt));
            }
        }
        throw new AssertionError("不可达:尝试次数至少为 1");
    }

    /**
     * 执行无返回值的任务直至成功或终止,语义与 {@link #call} 相同。
     *
     * @param task 要执行的任务,不能为 {@code null}
     * @throws RetryExhaustedException 所有尝试均失败且均判定为可重试时抛出
     * @throws InterruptedException    等待退避期间线程被中断时抛出(同时恢复中断状态)
     */
    public void run(CheckedRunnable task) throws Exception {
        Objects.requireNonNull(task, "task");
        call(() -> {
            task.run();
            return null;
        });
    }

    /** 等待退避时长;被中断时恢复中断状态并向上传播。 */
    private static void sleep(long nanos) throws InterruptedException {
        if (nanos <= 0) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    /** 类型擦除式抛出,使 {@link Error} 与受检异常都能原样传播。 */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }
}
