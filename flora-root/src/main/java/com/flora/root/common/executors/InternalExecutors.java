package com.flora.root.common.executors;

import com.flora.root.common.executors.impl.FloraThreadFactory;
import com.flora.root.common.executors.impl.LoggingScheduledThreadPoolExecutor;
import com.flora.root.common.executors.impl.LoggingThreadPoolExecutor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * flora-root 内部使用的共享执行器（按任务语义分派）。<b>仅供本模块内部组件调用，不对外导出。</b>
 * <p>基础库内部组件（如缓存后台刷新）按任务语义调用对应入口提交任务，避免各处自行创建线程池。
 * 对外不暴露 {@link java.util.concurrent.Executor} 或 {@link ExecutorService}，调用方只能以透传方式提交，
 * 从而无法误调用 {@code shutdown()} 等生命周期方法。</p>
 * <p>{@link TaskKind#COMPUTE} 不单独建池，直接复用 {@link ForkJoinPool#commonPool()}（JVM 级共享、随进程退出）；
 * 其余类别（IO / LIGHT / SCHEDULED）各自维护进程级单例池，懒加载构建、守护线程、<b>随 JVM 退出，不提供关闭 API</b>。</p>
 * <p>拒绝策略约定见 {@link TaskKind}：{@link TaskKind#IO} 与 {@link TaskKind#SCHEDULED} 在池饱和时
 * <b>静默丢弃</b>任务，提交此类任务即表示可接受丢弃（任务须可重触发或丢失可接受）。</p>
 */
@SuppressWarnings("resource")
public final class InternalExecutors {

    private static final ConcurrentHashMap<TaskKind, ExecutorService> POOLS = new ConcurrentHashMap<>();

    private InternalExecutors() {
    }

    /** 提交 CPU 密集任务（加解密、压缩、编解码、哈希）；复用 {@link ForkJoinPool#commonPool()}。 */
    public static void compute(Runnable task) {
        ForkJoinPool.commonPool().execute(task);
    }

    /** 提交阻塞 IO / 后台刷新 / 远程调用 / 异步刷盘等可重触发任务。 */
    public static void io(Runnable task) {
        pool(TaskKind.IO).execute(task);
    }

    /** 提交一次性短生命周期异步任务；按需扩缩，上限防线程无限增长。 */
    public static void light(Runnable task) {
        pool(TaskKind.LIGHT).execute(task);
    }

    /** 提交缓存后台刷新任务；等价于 {@link #io(Runnable)}。 */
    public static void refresh(Runnable task) {
        io(task);
    }

    /** 延迟执行一次性定时任务。 */
    public static void schedule(Runnable task, long delay, TimeUnit unit) {
        scheduled().schedule(task, delay, unit);
    }

    /** 以固定频率周期执行定时任务（错过即跳过，避免追赶风暴）。 */
    public static void scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        scheduled().scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    private static ExecutorService pool(TaskKind kind) {
        return POOLS.computeIfAbsent(kind, InternalExecutors::create);
    }

    private static ScheduledExecutorService scheduled() {
        return (ScheduledExecutorService) pool(TaskKind.SCHEDULED);
    }

    private static ExecutorService create(TaskKind kind) {
        FloraThreadFactory tf = new FloraThreadFactory("flora-" + kind.prefix());
        if (kind == TaskKind.SCHEDULED) {
            return new LoggingScheduledThreadPoolExecutor(kind.core(), tf, kind.rejected());
        }
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(kind.queueCapacity());
        return new LoggingThreadPoolExecutor(kind.core(), kind.max(),
                kind.keepAliveMillis(), TimeUnit.MILLISECONDS, queue, tf, kind.rejected());
    }
}
