package com.flora.sanctum.core.model.vault;

/**
 * 仓库时间戳（见设计 02"仓库时间戳"）。
 * <p>
 * 会话锚点 {@code baseTimestamp} = max(全库块时间戳上限, 当前毫秒)，并与 {@code startNanos} 在构造时
 * 同源捕获——锚点的墙钟读数即 startNanos 所在时刻，避免二者错位导致写入时间戳早于调用方采样时刻。
 * 锚点封顶于「当前毫秒 + 1 年」，防止异常巨大的块时间戳无限制前移锚点。
 * 写入时间戳 = {@code max(单调时钟偏移毫秒 + baseTimestamp, 全库当前最大时间戳)}。
 * <p>
 * nanoTime 用单调时钟（不受系统改时间影响），存储单位毫秒。锚点不写回 manifest，每次解锁重新计算。
 */
public final class WarehouseClock {

    private static final long ONE_YEAR_MILLIS = 365L * 24 * 3600 * 1000;

    private final long baseTimestamp;
    private final long startNanos;

    /** @param maxBlockTimestamp 全库块时间戳上限（由解锁方扫描全部块得出）。 */
    public WarehouseClock(long maxBlockTimestamp) {
        long now = System.currentTimeMillis();
        long anchor = Math.max(maxBlockTimestamp, now);
        this.baseTimestamp = Math.min(anchor, now + ONE_YEAR_MILLIS);
        this.startNanos = System.nanoTime();
    }

    public long baseTimestamp() {
        return baseTimestamp;
    }

    /** 会话内相对偏移（毫秒，向上取整）。
     *  向上取整确保写入时间戳不会因纳秒→毫秒截断而落后于同毫秒内更晚的墙钟采样，
     *  例如调用方在解锁后采样的 before 落在同一毫秒时，写入时间戳仍 ≥ before。 */
    private long sessionElapsedMillis() {
        return Math.ceilDiv(System.nanoTime() - startNanos, 1_000_000L);
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
