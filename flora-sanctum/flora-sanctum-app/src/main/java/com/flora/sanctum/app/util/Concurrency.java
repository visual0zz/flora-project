package com.flora.sanctum.app.util;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 集中式并发原语工厂：所有后台线程、线程池与定时任务的创建都应经由本类，
 * 以统一线程命名、守护化，并在未捕获异常时记入日志（而非静默丢弃或仅落到标准错误）。
 *
 * <p>约定：本类创建的所有线程均为守护线程，避免阻塞应用退出；每条线程都装有
 * per-thread 未捕获异常处理器，异常以 ERROR 级别记录。全局兜底仍由
 * LogSetup 的 {@code setDefaultUncaughtExceptionHandler} 提供，覆盖本类未创建的线程
 * （如 ForkJoinPool.commonPool、Swing EDT）。</p>
 *
 * <p>为何要收拢：散落的 {@code new Thread} / {@code Executors} / {@code java.util.Timer}
 * 命名不可辨识，且 {@code TimerTask.run()} 一旦抛出未捕获异常会杀死整个 Timer 线程、
 * 令后续定时任务静默停摆。集中到此处可保证任何线程上的异常都被日志捕获。</p>
 */
public final class Concurrency {

    private static final Logger LOG = LoggerFactory.getLogger(Concurrency.class);

    /** 共享单线程调度器：供一次性延迟任务（如剪贴板清除、toast 自动消失）复用。 */
    private static final ScheduledExecutorService SCHEDULER =
            newScheduledThreadPool(1, "sanctum-scheduler");

    private Concurrency() {
    }

    /** 命名守护线程工厂：线程名 {@code prefix-N}，未捕获异常记 ERROR。 */
    public static ThreadFactory threadFactory(String prefix) {
        return new NamedThreadFactory(prefix, true);
    }

    /** 命名守护线程池（核心=最大=n）。 */
    public static ExecutorService newFixedThreadPool(int n, String prefix) {
        return java.util.concurrent.Executors.newFixedThreadPool(n, threadFactory(prefix));
    }

    /** 命名守护定时线程池。 */
    public static ScheduledExecutorService newScheduledThreadPool(int n, String prefix) {
        return java.util.concurrent.Executors.newScheduledThreadPool(n, threadFactory(prefix));
    }

    /**
     * 启动一条命名守护线程；任务体未捕获异常记 ERROR 后不向上抛（循环类任务据此存活）。
     */
    public static Thread start(String name, Runnable task) {
        Thread t = new Thread(guard(name, task), name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** 经共享调度器安排一次性延迟任务，返回 future 供取消。 */
    public static ScheduledFuture<?> schedule(String name, Runnable task, long delay, TimeUnit unit) {
        return SCHEDULER.schedule(guard(name, task), delay, unit);
    }

    /**
     * 包装任务体：捕获 Throwable 记 ERROR（不重抛），用于定时器 / future 体兜底，
     * 避免单个任务异常拖垮整个调度器或线程。
     */
    public static Runnable guard(String where, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                LOG.error("Uncaught in {}: {}", where, t.getMessage(), t);
            }
        };
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final boolean daemon;
        private final AtomicLong seq = new AtomicLong();

        NamedThreadFactory(String prefix, boolean daemon) {
            this.prefix = prefix;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(guard(prefix, r), prefix + "-" + seq.incrementAndGet());
            t.setDaemon(daemon);
            t.setUncaughtExceptionHandler((thread, throwable) ->
                    LOG.error("Uncaught in thread {}", thread.getName(), throwable));
            return t;
        }
    }
}
