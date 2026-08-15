package com.flora.sanctum.model;

/**
 * 仓库时间戳（见设计 02"仓库时间戳"）。
 * <p>
 * 每块写入时 {@code updateTimestamp = max(
 *   (nanoTimeNow − 启动nanoTime)毫秒 + warehouseTime,
 *   仓库中所有文件的最大 updateTimestamp )}。
 * <p>
 * nanoTime 用单调时钟（不受系统改时间影响），存储单位毫秒。
 * warehouseTime 初值 1，仅在关闭库时更新。
 */
public final class WarehouseClock {

    private long warehouseTime;
    private final long startNanos;

    public WarehouseClock(long warehouseTime) {
        this.warehouseTime = warehouseTime;
        this.startNanos = System.nanoTime();
    }

    public long warehouseTime() {
        return warehouseTime;
    }

    /** 会话内相对偏移（毫秒）。 */
    private long sessionElapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** 当前建议写入时间戳（仓库锚点 + 会话偏移），未与全库 max 取齐。 */
    public long suggestedTimestamp() {
        return sessionElapsedMillis() + warehouseTime;
    }

    /** 与给定最大值取齐后的最终写入时间戳。 */
    public long nextTimestamp(long maxExisting) {
        long suggested = suggestedTimestamp();
        return Math.max(suggested, maxExisting);
    }

    /** 关闭库时更新 warehouseTime 为当前建议值。 */
    public void close() {
        this.warehouseTime = suggestedTimestamp();
    }
}
