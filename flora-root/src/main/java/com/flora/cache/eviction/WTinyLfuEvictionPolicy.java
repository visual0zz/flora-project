package com.flora.cache.eviction;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * W-TinyLFU 淘汰策略（key 基于，与具体存储实现无关）。
 * <p>
 * 结构（总容量 = capacity）：
 * <ul>
 *   <li><b>准入窗口（Window LRU）</b>：约占 1% 容量，新条目先进入窗口，
 *       采用 LRU 淘汰；被窗口淘汰的条目成为候选，尝试进入主区。</li>
 *   <li><b>主区（SLRU）</b>：约占 99% 容量，分为 probation（观察段，占主区 80%）
 *       与 protected（保护段，占主区 20%）。命中时条目从 probation 晋升到
 *       protected（LRU 头部）；protected 满时按 LRU 降级回 probation。</li>
 *   <li><b>频率统计（Count-Min Sketch）</b>：记录所有 key（含已淘汰）的历史访问频率。
 *       窗口候选与主区受害者比较频率：候选频率 &gt; 受害者才准入（TinyLFU 准入策略）。
 *       统计量达到样本上限时整体减半（aging）。</li>
 * </ul>
 * <p>
 * 线程安全：三段 LRU 各由一把 {@link ReentrantLock} 保护；分段只是 key 的顺序索引，
 * 允许与容量计数短暂不一致；锁顺序固定为 windowLock → probationLock → protectedLock。
 * 容量判断通过 {@code sizeOf} 读取存储当前条目数。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public final class WTinyLfuEvictionPolicy<K, V> implements EvictionPolicy<K, V> {

    /** 窗口区占比 1%（至少 1） */
    private static final int WINDOW_PERCENT = 1;
    /** 主区中 probation 段占比 80% */
    private static final int PROBATION_PERCENT = 80;

    // region 取值
    private static final int R_WINDOW = 0;
    private static final int R_PROBATION = 1;
    private static final int R_PROTECTED = 2;
    private static final int R_DETACHED = -1; // 已从所有分段摘除

    // ---- 三段 LRU（accessOrder；value 仅占位，用 key 自身） ----
    private final LinkedHashMap<K, K> window = new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<K, K> probation = new LinkedHashMap<>(16, 0.75f, true);
    private final LinkedHashMap<K, K> protectedSeg = new LinkedHashMap<>(16, 0.75f, true);

    private final ReentrantLock windowLock = new ReentrantLock();
    private final ReentrantLock probationLock = new ReentrantLock();
    private final ReentrantLock protectedLock = new ReentrantLock();

    /** key → 当前所在分段；R_DETACHED 表示已从分段摘除但可能仍在存储中 */
    private final Map<K, Integer> region = new java.util.concurrent.ConcurrentHashMap<>();

    private final FrequencySketch sketch;
    private final LongSupplier sizeOf; // 当前存储条目数（容量判断用）
    private final long capacity;
    private final int windowMax;
    private final int mainMax;
    private final int probationMax;

    /**
     * @param capacity 容量上限（{@code <=0} 表示无上限，永不淘汰）
     * @param sizeOf   读取当前存储条目数的函数（用于容量判断与兜底强删）
     */
    public WTinyLfuEvictionPolicy(long capacity, LongSupplier sizeOf) {
        this.capacity = capacity;
        this.sizeOf = sizeOf;
        long cap = Math.max(1, capacity);
        this.windowMax = (int) Math.max(1, cap * WINDOW_PERCENT / 100);
        this.mainMax = (int) Math.max(1, cap - windowMax);
        this.probationMax = (int) Math.max(1, mainMax * PROBATION_PERCENT / 100);
        this.sketch = new FrequencySketch(cap);
    }

    // ========== 内部辅助 ==========

    private int windowSize() {
        windowLock.lock();
        try {
            return window.size();
        } finally {
            windowLock.unlock();
        }
    }

    // ---- Count-Min Sketch（4-bit 计数，上限 15） ----

    /**
     * TinyLFU 频率估计器。4 个哈希函数、4-bit 饱和计数，
     * 样本量达到 {@link #sampleSize} 时所有计数减半（aging）。
     */
    private static final class FrequencySketch {
        private static final int MAX_COUNT = 15;
        private static final long[] SEED = {0xc3a5c85c97cb3127L, 0xb492b66fbe98f273L,
                0x9ae16a3b2f90404fL, 0x9e3779b97f4a7c15L};

        private final byte[] table;  // 每字节两个 4-bit 计数
        private final int size;      // 计数个数（2 的幂）
        private final long sampleSize;
        private long count;

        FrequencySketch(long capacity) {
            int s = 64;
            while (s < Math.max(64L, capacity * 10)) s <<= 1;
            size = s;
            table = new byte[size >>> 1];
            sampleSize = 10L * Math.max(1, capacity);
        }

        int estimate(Object key) {
            long h = mix(key.hashCode());
            int est = Integer.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                est = Math.min(est, get(index(h, i)));
            }
            return est;
        }

        void increment(Object key) {
            long h = mix(key.hashCode());
            for (int i = 0; i < 4; i++) {
                int idx = index(h, i);
                if (get(idx) < MAX_COUNT) set(idx, get(idx) + 1);
            }
            if (++count >= sampleSize) reset();
        }

        private void reset() {
            count >>>= 1;
            for (int i = 0; i < table.length; i++) {
                int b = table[i] & 0xFF;
                table[i] = (byte) (((b & 0x0F) >>> 1) | ((((b >>> 4) & 0x0F) >>> 1) << 4));
            }
        }

        private int index(long h, int i) {
            return (int) ((h + SEED[i] * (i + 1)) & (size - 1));
        }

        private int get(int idx) {
            int b = table[idx >>> 1] & 0xFF;
            return (idx & 1) == 0 ? (b & 0x0F) : (b >>> 4);
        }

        private void set(int idx, int v) {
            int i = idx >>> 1;
            int b = table[i] & 0xFF;
            table[i] = (byte) ((idx & 1) == 0 ? (b & 0xF0) | v : (b & 0x0F) | (v << 4));
        }

        private static long mix(int x) {
            long z = (x & 0xFFFFFFFFL) * 0x9E3779B97F4A7C15L;
            z ^= z >>> 33;
            return z;
        }
    }

    // ========== EvictionPolicy 回调 ==========

    @Override
    public void onPut(K key, boolean existed) {
        sketch.increment(key);
        region.put(key, R_WINDOW);
        windowLock.lock();
        try {
            window.put(key, key);
        } finally {
            windowLock.unlock();
        }
    }

    @Override
    public void onGet(K key, boolean existed) {
        sketch.increment(key); // 读取即需求：累加频率素描（命中/未命中皆为需求预热）
    }

    @Override
    public void onTouch(K key, boolean existed) {

    }

    @Override
    public void onMutate(K key, boolean existed) {
        sketch.increment(key);
        if (!existed) return; // 操作前未驻留：仅记需求，不纳入淘汰候选段（修复缺失键污染 window）
        Integer r = region.get(key);
        if (r == null) return;
        switch (r) {
            case R_WINDOW -> {
                windowLock.lock();
                try {
                    if (region.get(key) == R_WINDOW) window.get(key); // LRU touch
                } finally {
                    windowLock.unlock();
                }
            }
            case R_PROBATION -> promoteToProtected(key);
            case R_PROTECTED -> {
                protectedLock.lock();
                try {
                    if (region.get(key) == R_PROTECTED) protectedSeg.get(key);
                } finally {
                    protectedLock.unlock();
                }
            }
            default -> { /* R_DETACHED：已被摘除（并发淘汰/删除），忽略 */ }
        }
    }

    @Override
    public void onAccess(K key, boolean existed) {

    }

    @Override
    public void onInvalidate(K key) {
        windowLock.lock();
        try {
            window.remove(key);
        } finally {
            windowLock.unlock();
        }
        probationLock.lock();
        try {
            probation.remove(key);
        } finally {
            probationLock.unlock();
        }
        protectedLock.lock();
        try {
            protectedSeg.remove(key);
        } finally {
            protectedLock.unlock();
        }
        region.remove(key);
    }

    @Override
    public K selectVictim() {
        if (capacity <= 0) return null;
        // 阶段一：窗口超额走 W-TinyLFU 淘汰。循环至窗口降到 windowMax 或容量足够。
        while (windowSize() > windowMax && sizeOf.getAsLong() > capacity - windowMax) {
            K v = evictFromWindow();
            if (v != null) return v; // 本步真正删除了一个存储项，交由挂载它的缓存负责删除该 key
        }
        // 极端回退：仍超容时直接从 probation 尾部强删
        if (sizeOf.getAsLong() > capacity) {
            K victim = pollEldest(probationLock, probation);
            if (victim != null) {
                region.remove(victim);
                return victim;
            }
        }
        return null;
    }

    @Override
    public void onClear() {
        windowLock.lock();
        try {
            window.clear();
        } finally {
            windowLock.unlock();
        }
        probationLock.lock();
        try {
            probation.clear();
        } finally {
            probationLock.unlock();
        }
        protectedLock.lock();
        try {
            protectedSeg.clear();
        } finally {
            protectedLock.unlock();
        }
        region.clear();
    }

    // ========== 内部：分段迁移 ==========

    /** 记录一次访问（命中时调用）：分段内晋升。 */
    private void promoteToProtected(K key) {
        probationLock.lock();
        try {
            Integer rk = region.get(key);
            if (rk == null || rk != R_PROBATION) return; // 已被并发摘除/迁移
            probation.remove(key);
            region.put(key, R_PROTECTED);
            protectedLock.lock();
            try {
                protectedSeg.put(key, key);
                while (protectedSeg.size() > mainMax - probationMax) {
                    K demoted = pollEldest(protectedLock, protectedSeg);
                    if (demoted == null) break;
                    region.put(demoted, R_PROBATION);
                    probation.put(demoted, demoted); // probationLock 可重入
                }
            } finally {
                protectedLock.unlock();
            }
        } finally {
            probationLock.unlock();
        }
    }

    /** 移除并返回某分段 LRU 最老 key（调用方须持有对应锁）。 */
    private static <K> K pollEldest(ReentrantLock lock, LinkedHashMap<K, K> seg) {
        lock.lock();
        try {
            Iterator<Map.Entry<K, K>> it = seg.entrySet().iterator();
            if (!it.hasNext()) return null;
            K k = it.next().getKey();
            it.remove();
            return k;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 从 probation 挑选主区淘汰受害者（LRU 最老）。
     * 返回的 key 已从 probation 摘除且 region 置为 R_DETACHED。
     */
    private K pickMainVictim() {
        probationLock.lock();
        try {
            Iterator<Map.Entry<K, K>> it = probation.entrySet().iterator();
            while (it.hasNext()) {
                K k = it.next().getKey();
                it.remove();
                region.put(k, R_DETACHED);
                return k;
            }
            return null;
        } finally {
            probationLock.unlock();
        }
    }

    /**
     * 淘汰路径：window 满时挤出 LRU 候选，经 TinyLFU 准入判定后进入主区或被拒绝。
     * 返回需要从存储中删除的 key（候选被拒、或受害者被逐）；若本步无需删除存储则返回 null。
     */
    private K evictFromWindow() {
        K candidate;
        windowLock.lock();
        try {
            candidate = pollEldest(windowLock, window);
        } finally {
            windowLock.unlock();
        }
        if (candidate == null) return null;
        region.put(candidate, R_DETACHED); // 先置 DETACHED，杜绝幽灵

        K victim = pickMainVictim();
        if (victim == null) {
            admit(candidate);
            return null; // 无主区受害者，候选直接准入，本步不删存储
        }
        int candidateFreq = sketch.estimate(candidate);
        int victimFreq = sketch.estimate(victim);
        if (candidateFreq > victimFreq) {
            // 候选更热：淘汰受害者，候选入主区
            region.remove(victim);
            admit(candidate);
            return victim;
        } else {
            // 拒绝候选：候选从存储删除；受害者保持 DETACHED
            region.put(candidate, R_DETACHED);
            return candidate;
        }
    }

    /** 候选通过准入判定，进入主区 probation 段。 */
    private void admit(K key) {
        probationLock.lock();
        try {
            Integer rk = region.get(key);
            if (rk == null || rk != R_DETACHED) return; // 已被并发安置，放弃
            region.put(key, R_PROBATION);
            probation.put(key, key);
        } finally {
            probationLock.unlock();
        }
    }
}
