package com.flora.common.executors.impl;

import com.flora.runtime.log.Logger;
import com.flora.runtime.log.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 共享线程池的统一线程工厂。
 * <p>生成的线程名为 {@code <prefix>-<序号>}（如 {@code flora-compute-1}），且均为守护线程，
 * 不会阻止 JVM 退出。线程的 {@link Thread.UncaughtExceptionHandler} 仅覆盖线程自身异常；
 * 提交任务的异常由执行器在 {@link LoggingThreadPoolExecutor#afterExecute} 中记录。</p>
 */
public final class FloraThreadFactory implements ThreadFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger("com.flora.common.executors");

    private final String prefix;
    private final AtomicLong seq = new AtomicLong();

    public FloraThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, prefix + "-" + seq.incrementAndGet());
        t.setDaemon(true);
        t.setUncaughtExceptionHandler((thread, e) ->
                LOGGER.error("共享线程 {} 抛出未捕获异常", e));
        return t;
    }
}
