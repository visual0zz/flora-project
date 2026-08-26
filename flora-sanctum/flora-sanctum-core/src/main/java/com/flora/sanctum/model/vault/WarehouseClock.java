package com.flora.sanctum.model.vault;

/**
 * 仓库时间戳（见设计 02"仓库时间戳"）。
 * <p>
 * 会话锚点 = 解锁时扫描到的全库所有块时间戳的最大值 {@code baseTimestamp}；
 * 写入时间戳 = {@code max(单调时钟偏移毫秒 + baseTimestamp, 全库当前最大时间戳)}。
 * <p>
 * nanoTime 用单调时钟（不受系统改时间影响），存储单位毫秒。锚点由解锁方
 * {@code VaultUnlocker} 扫描全部块后计算传入，不再持久化到 manifest。
 */
public final class WarehouseClock {

    private final long baseTimestamp;
    private final long startNanos;

    public WarehouseClock(long baseTimestamp) {
        this.baseTimestamp = baseTimestamp;
        this.startNanos = System.nanoTime();
    }

    public long baseTimestamp() {
        return baseTimestamp;
    }

    /** 会话内相对偏移（毫秒）。 */
    private long sessionElapsedMillis() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** 未与全库最大时间戳取齐的候选写入时间戳（会话锚点 + 会话偏移）。 */
    public long unclampedTimestamp() {
        return sessionElapsedMillis() + baseTimestamp;
    }

    /** 与给定最大值取齐后的最终写入时间戳。 */
    public long timestampCappedAt(long maxExisting) {
        long suggested = unclampedTimestamp();
        return Math.max(suggested, maxExisting);
    }
}
