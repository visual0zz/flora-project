package com.flora.common.executors;

import com.flora.common.executors.impl.FloraThreadFactory;
import com.flora.common.executors.impl.LoggingScheduledThreadPoolExecutor;
import com.flora.common.executors.impl.LoggingThreadPoolExecutor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

/**
 * flora-root 内按任务语义分池的共享线程池注册表。
 * <p>基础库各组件（如缓存后台刷新）按 {@link TaskKind} 取用对应执行器，避免各处自行创建线程池。
 * 所有池为进程级单例，懒加载构建；线程均为守护线程，<b>生命周期随 JVM，不提供关闭 API</b>，
 * 宿主不应也无法关闭这些池。</p>
 * <p>返回窄接口 {@link Executor}（而非 {@link ExecutorService}），防止调用方误调用 {@code shutdown()}。
 * {@link #scheduled()} 额外返回 {@link ScheduledExecutorService} 以暴露定时能力。</p>
 * <p>拒绝策略约定见 {@link TaskKind}：{@link TaskKind#IO} 与 {@link TaskKind#SCHEDULED} 在池饱和时
 * <b>静默丢弃</b>任务，提交此类任务即表示可接受丢弃（任务须可重触发或丢失可接受）。</p>
 */
public final class ExecutorPools {

    private static final ConcurrentHashMap<TaskKind, ExecutorService> POOLS = new ConcurrentHashMap<>();

    private ExecutorPools() {
    }

    /** 取指定语义类别的共享执行器（进程级单例）。 */
    public static Executor executor(TaskKind kind) {
        return pool(kind);
    }

    /** 取定时/周期任务专用的共享执行器。 */
    public static ScheduledExecutorService scheduled() {
        return (ScheduledExecutorService) pool(TaskKind.SCHEDULED);
    }

    /** 缓存后台刷新专用执行器；等价于 {@link #executor(TaskKind)}{@code (TaskKind.IO)}。 */
    public static Executor refresh() {
        return executor(TaskKind.IO);
    }

    private static ExecutorService pool(TaskKind kind) {
        return POOLS.computeIfAbsent(kind, ExecutorPools::create);
    }

    private static ExecutorService create(TaskKind kind) {
        FloraThreadFactory tf = new FloraThreadFactory("flora-" + kind.prefix());
        if (kind == TaskKind.SCHEDULED) {
            return new LoggingScheduledThreadPoolExecutor(kind.core(), tf, kind.rejected());
        }
        BlockingQueue<Runnable> queue = (kind == TaskKind.LIGHT)
                ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(kind.queueCapacity());
        return new LoggingThreadPoolExecutor(kind.core(), kind.max(),
                kind.keepAliveMillis(), TimeUnit.MILLISECONDS, queue, tf, kind.rejected());
    }
}
