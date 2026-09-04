package com.flora.sanctum.app;

import javax.swing.SwingUtilities;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;
import com.flora.sanctum.app.util.Concurrency;

/**
 * 单线程后台任务执行器：串行执行导入 / 导出 / 远程同步等任务，空闲时按最后活跃时间
 * 轮询自动锁定（见设计文档 idea*-sanctum-backend-thread）。
 *
 * <p>循环语义：取任务 → 有则执行（执行期间不判定锁定，故「导入中绝不锁定」天然成立）→
 * 无任务且仍解锁且空闲超过阈值则触发锁定 → 否则睡眠 1 秒再查。
 * 界面交互（鼠标 / 键盘 / 各操作）经 {@link #markActive()} 刷新时间戳，避免误锁。</p>
 *
 * <p>约定：所有回调（{@link Listener}）均在 EDT 上调用，可直接操作 Swing；
 * 锁定时由后台线程经 {@link SwingUtilities#invokeLater} 切回 EDT 执行，避免在非 EDT 触碰 UI。</p>
 */
public final class BackgroundExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(BackgroundExecutor.class);

    /** 一个后台任务：带名称（用于 UI 报告）与执行体。 */
    public interface Task {
        /** 任务名（用于状态栏「正在{name}…」与转圈提示）。 */
        String name();

        /** 执行体；抛异常由 {@link Listener#onFailure} 兜底报告。 */
        void run() throws Exception;
    }

    /** 任务生命周期回调（均在 EDT 上调用）。 */
    public interface Listener {
        /** 任务开始：显示转圈与「正在{name}…」。 */
        void onStart(String name);

        /** 任务正常结束：隐藏转圈。 */
        void onEnd(String name);

        /** 任务异常：隐藏转圈并报告错误。 */
        void onFailure(String name, Throwable t);
    }

    private final LinkedBlockingQueue<Task> queue = new LinkedBlockingQueue<>();
    private final AtomicLong lastActivityMillis = new AtomicLong(nowMillis());
    private final Thread worker;
    private volatile boolean running = true;

    private final LongSupplier idleTimeoutMillis;
    private final BooleanSupplier unlocked;
    private final Runnable lockNow;
    private final Listener listener;

    public BackgroundExecutor(LongSupplier idleTimeoutMillis, BooleanSupplier unlocked,
                              Runnable lockNow, Listener listener) {
        this.idleTimeoutMillis = idleTimeoutMillis;
        this.unlocked = unlocked;
        this.lockNow = lockNow;
        this.listener = listener;
        this.worker = Concurrency.start("sanctum-background", this::loop);
    }

    /** 提交任务；提交本身视为一次活跃，刷新时间戳。 */
    public void submit(Task task) {
        markActive();
        queue.add(task);
    }

    /** 界面活动：刷新最后活跃时间，避免空闲锁定。 */
    public void markActive() {
        lastActivityMillis.set(nowMillis());
    }

    /** 停止后台线程（应用退出时）。 */
    public void shutdown() {
        running = false;
        worker.interrupt();
    }

    private static long nowMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private void loop() {
        while (running) {
            Task task = queue.poll();
            if (task != null) {
                runTask(task);
                continue;
            }
            // 空闲：仅当仍解锁时才判定超时（已锁定则只睡眠待命，等待下次解锁重置计时）
            if (unlocked.getAsBoolean()) {
                long idle = nowMillis() - lastActivityMillis.get();
                if (idle > idleTimeoutMillis.getAsLong()) {
                    LOG.info("Idle timeout ({}ms) reached, triggering auto-lock", idle);
                    SwingUtilities.invokeLater(lockNow);
                    markActive(); // 锁定后刷新，避免解锁前这一窗口内反复触发
                    continue;
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                if (!running) {
                    break;
                }
            }
        }
    }

    private void runTask(Task task) {
        LOG.info("Task started: {}", task.name());
        try {
            SwingUtilities.invokeLater(() -> listener.onStart(task.name()));
            task.run();
        } catch (Throwable t) {
            LOG.error("Task failed: {}", task.name(), t);
            SwingUtilities.invokeLater(() -> listener.onFailure(task.name(), t));
            return;
        }
        LOG.info("Task finished: {}", task.name());
        SwingUtilities.invokeLater(() -> listener.onEnd(task.name()));
        markActive(); // 任务结束续命：从完成时刻起重算空闲阈值（细节2 甲）
    }
}
