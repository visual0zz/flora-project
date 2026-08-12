package com.flora.root.common.executors.impl;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 重写 {@link #afterExecute} 的 {@link ThreadPoolExecutor}：统一记录任务抛出的未捕获异常。
 * <p>标准线程池不会为通过 {@code execute} 提交的 {@link Runnable} 触发线程的
 * {@link Thread.UncaughtExceptionHandler}，因此任务异常只能在此处兜底记录，避免静默丢失。
 * 经运行时日志门面输出，不向上抛出（守护线程场景下无更高层可捕获）。</p>
 */
public class LoggingThreadPoolExecutor extends ThreadPoolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingThreadPoolExecutor.class);

    public LoggingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                     TimeUnit unit, BlockingQueue<Runnable> workQueue,
                                     ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        // execute(Runnable) 抛出的异常以 t 传入；submit(...) 被包装为 Future，需从中取出。
        if (t == null && r instanceof Future<?>) {
            try {
                ((Future<?>) r).get();
            } catch (CancellationException ce) {
                t = ce;
            } catch (ExecutionException ee) {
                t = ee.getCause();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        if (t != null) {
            LOGGER.error("任务在共享线程池执行中抛出异常", t);
        }
    }
}
