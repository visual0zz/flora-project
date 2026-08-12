package com.flora.root.common.executors;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 任务语义类别，决定其所归属的共享执行器。
 * <p>
 * 基础库按任务的计算特征而非业务功能分派，调用方按语义取用对应入口
 * （见 flora-root 内部类 {@code InternalExecutors}，不对外导出）。各池参数基于可用处理器数动态推算，且均为守护线程、随 JVM 退出。
 * <p>
 * 分派与拒绝策略约定：
 * <ul>
 *   <li>{@link #COMPUTE} 不单独建池，复用 {@link java.util.concurrent.ForkJoinPool#commonPool()}
 *       （JVM 级共享、无界工作窃取，不会拒绝任务）；</li>
 *   <li>{@link #LIGHT} 为一次性短生命周期异步任务，有界队列 + {@link ThreadPoolExecutor.DiscardPolicy}：
 *       溢出即静默丢弃（轻量任务可重触发或丢失可接受），<b>绝不</b>由调用方线程兜底，
 *       以免阻塞业务请求线程；</li>
 *   <li>{@link #IO} 与 {@link #SCHEDULED} 同样使用 {@link ThreadPoolExecutor.DiscardPolicy}，
 *       池饱和时静默丢弃。提交此类任务即表示接受可能被丢弃：
 *       任务须可重触发或丢失可接受（如缓存后台刷新，调用方下次读取会再次触发）。</li>
 * </ul>
 * <p>
 * 注意：{@link #SCHEDULED} 为全进程唯一的定时池，所有周期任务串行复用其有限线程；
 * 单个长耗时周期任务会拖慢其余定时任务，故周期任务应保证短小、非阻塞。
 */
public enum TaskKind {

    /** CPU 密集任务（加解密、压缩、编解码、哈希），不阻塞；复用 ForkJoinPool.commonPool，无专属池与拒绝策略。 */
    COMPUTE("compute"),

    /** 阻塞 IO / 后台刷新 / 远程调用 / 异步刷盘，可重触发；少量常驻线程 + 突发扩容，饱和静默丢弃。 */
    IO("io", 4, Math.max(8, cpus() * 2), 1024, 60_000L, new ThreadPoolExecutor.DiscardPolicy()),

    /** 定时 / 周期任务（心跳、周期刷新、超时清理）；错过即跳过，避免追赶风暴。单池串行，任务须短小。 */
    SCHEDULED("sched", Math.max(2, cpus() / 2), Math.max(2, cpus() / 2), 0, 0L, new ThreadPoolExecutor.DiscardPolicy()),

    /** 一次性短生命周期异步任务；有界队列防内存膨胀，溢出静默丢弃，避免阻塞调用方线程。 */
    LIGHT("light", 0, Math.min(cpus() * 8, 256), 1024, 60_000L, new ThreadPoolExecutor.DiscardPolicy());

    private static int cpus() {
        return Runtime.getRuntime().availableProcessors();
    }

    private final String prefix;
    private final int core;
    private final int max;
    /** 有界队列容量；{@code 0} 表示特殊队列（{@link #SCHEDULED} 用 DelayedWorkQueue，{@link #LIGHT} 用 SynchronousQueue 之外的有界队列）。 */
    private final int queueCapacity;
    private final long keepAliveMillis;
    private final RejectedExecutionHandler rejected;

    /** 无专属池的类别（如 {@link #COMPUTE}）：仅携带线程名前缀。 */
    TaskKind(String prefix) {
        this(prefix, 0, 0, 0, 0L, null);
    }

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

    /** 核心线程数（无专属池的类别返回 0）。 */
    int core() {
        return core;
    }

    /** 最大线程数（无专属池的类别返回 0）。 */
    int max() {
        return max;
    }

    /** 有界队列容量；{@code 0} 表示该类别使用特殊队列。 */
    int queueCapacity() {
        return queueCapacity;
    }

    /** 空闲线程存活时间（毫秒，无专属池的类别返回 0）。 */
    long keepAliveMillis() {
        return keepAliveMillis;
    }

    /** 饱和时的拒绝策略（无专属池的类别返回 null）。 */
    RejectedExecutionHandler rejected() {
        return rejected;
    }
}
