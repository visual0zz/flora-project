package com.flora.common.executors;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 任务语义类别，决定其所归属的共享线程池的默认参数。
 * <p>
 * 基础库按任务的计算特征而非业务功能分池，调用方按语义取用对应执行器
 * （见 {@link ExecutorPools}）。各池参数基于可用处理器数动态推算，且均为守护线程、随 JVM 退出。
 * <p>
 * 拒绝策略约定：
 * <ul>
 *   <li>{@link #COMPUTE} 与 {@link #LIGHT} 使用 {@link ThreadPoolExecutor.CallerRunsPolicy}，
 *       池饱和时由提交线程兜底执行，<b>不丢失任务</b>；</li>
 *   <li>{@link #IO} 与 {@link #SCHEDULED} 使用 {@link ThreadPoolExecutor.DiscardPolicy}，
 *       池饱和时<b>静默丢弃</b>。提交此类任务即表示接受可能被丢弃：
 *       任务须可重触发或丢失可接受（如缓存后台刷新，调用方下次读取会再次触发）。</li>
 * </ul>
 */
public enum TaskKind {

    /** CPU 密集任务（加解密、压缩、编解码、哈希），不阻塞、线程数约等于核数。 */
    COMPUTE("compute", cpus(), cpus(), Math.max(256, cpus() * 64), 0L, new ThreadPoolExecutor.CallerRunsPolicy()),

    /** 阻塞 IO / 后台刷新 / 远程调用 / 异步刷盘，可重触发；线程数多于核数。 */
    IO("io", Math.max(2, cpus()), cpus() * 2, 1024, 60_000L, new ThreadPoolExecutor.DiscardPolicy()),

    /** 定时 / 周期任务（心跳、周期刷新、超时清理）；错过即跳过，避免追赶风暴。 */
    SCHEDULED("sched", Math.max(1, cpus() / 2), Math.max(1, cpus() / 2), 0, 0L, new ThreadPoolExecutor.DiscardPolicy()),

    /** 一次性短生命周期异步任务；按需扩缩，上限防线程无限增长。 */
    LIGHT("light", 0, Math.min(cpus() * 8, 256), 0, 60_000L, new ThreadPoolExecutor.CallerRunsPolicy());

    private static int cpus() {
        return Runtime.getRuntime().availableProcessors();
    }

    private final String prefix;
    private final int core;
    private final int max;
    /** 有界队列容量；{@code 0} 表示特殊队列（{@link #SCHEDULED} 用 DelayedWorkQueue，{@link #LIGHT} 用 SynchronousQueue）。 */
    private final int queueCapacity;
    private final long keepAliveMillis;
    private final RejectedExecutionHandler rejected;

    TaskKind(String prefix, int core, int max, int queueCapacity, long keepAliveMillis,
             RejectedExecutionHandler rejected) {
        this.prefix = prefix;
        this.core = core;
        this.max = max;
        this.queueCapacity = queueCapacity;
        this.keepAliveMillis = keepAliveMillis;
        this.rejected = rejected;
    }

    /** 线程名前缀（形如 {@code flora-compute-}）。 */
    String prefix() {
        return prefix;
    }

    /** 核心线程数。 */
    int core() {
        return core;
    }

    /** 最大线程数。 */
    int max() {
        return max;
    }

    /** 有界队列容量；{@code 0} 表示该类别使用特殊队列。 */
    int queueCapacity() {
        return queueCapacity;
    }

    /** 空闲线程存活时间（毫秒）。 */
    long keepAliveMillis() {
        return keepAliveMillis;
    }

    /** 饱和时的拒绝策略。 */
    RejectedExecutionHandler rejected() {
        return rejected;
    }
}
