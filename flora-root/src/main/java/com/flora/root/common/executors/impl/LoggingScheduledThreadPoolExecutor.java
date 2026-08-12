package com.flora.root.common.executors.impl;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * 重写 {@link #afterExecute} 的 {@link ScheduledThreadPoolExecutor}：统一记录定时/周期任务
 * 抛出的未捕获异常。周期任务抛出异常后按 JDK 约定停止后续调度，此处仅记录以便排查。
 */
public class LoggingScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingScheduledThreadPoolExecutor.class);

    public LoggingScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory,
                                              RejectedExecutionHandler handler) {
        super(corePoolSize, threadFactory, handler);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
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
            LOGGER.error("定时任务在共享线程池执行中抛出异常", t);
        }
    }
}
