package com.flora.root.concurrent.retry;

/**
 * 可抛出受检异常的无参动作,配合 {@link Retryer#run} 使用。
 */
@FunctionalInterface
public interface CheckedRunnable {

    /**
     * 执行动作。
     *
     * @throws Exception 执行过程中抛出的任意异常
     */
    void run() throws Exception;
}
