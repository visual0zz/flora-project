package com.flora.cache.store;

import com.flora.cache.BoundedCacheStore;
import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.tag.WorkInProgress;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * W-TinyLFU + TTL 缓存实现。
 * <p>
 * 结构（总容量 = capacity）：
 * </p>
 * <ul>
 *   <li><b>准入窗口（Window LRU）</b>：约占 1% 容量，新条目先进入窗口，
 *       采用 LRU 淘汰；被窗口淘汰的条目成为候选，尝试进入主区。</li>
 *   <li><b>主区（SLRU）</b>：约占 99% 容量，分为 probation（观察段，占主区 80%）
 *       与 protected（保护段，占主区 20%）。命中时条目从 probation 晋升到
 *       protected（LRU 头部）；protected 满时按 LRU 降级回 probation。</li>
 *   <li><b>频率统计（Count-Min Sketch）</b>：记录所有 key（含已淘汰）的
 *       历史访问频率。窗口候选与主区受害者比较频率：候选频率 &gt; 受害者
 *       才准入（TinyLFU 准入策略），否则拒绝候选，保护主区免受突发流量冲击。
 *       统计量达到样本上限时整体减半（aging），防止历史数据固化。</li>
 * </ul>
 * <p>
 * <b>线程安全设计</b>：
 * </p>
 * <ul>
 *   <li>主表 {@link ConcurrentHashMap}，提供 key 级的原子 put/remove/compute。</li>
 *   <li>三个 LRU 分段各由一把 {@link ReentrantLock} 保护；条目的
 *       {@code region} 字段只在持有对应分段锁时读写，保证"读到的 region"
 *       与"实际所在分段"一致，杜绝跨分段竞态。</li>
 *   <li>分段只是主表的顺序索引，允许与主表短暂不一致；所有遍历路径
 *       （pickMainVictim / evictFromWindow）发现主表已删的索引条目时
 *       顺手清理（自愈），幽灵条目会被识别并回收。</li>
 *   <li>锁顺序固定为 windowLock → probationLock → protectedLock，
 *       嵌套取锁严格按此顺序，无死锁。</li>
 *   <li>淘汰通过 {@link AtomicBoolean} try-lock 串行化，容量为软上限，
 *       未抢到淘汰权的线程直接返回，不阻塞。</li>
 *   <li>Count-Min Sketch 由独立小锁保护，仅统计路径使用。</li>
 * </ul>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
@WorkInProgress
public final class MemoryCache<K, V> implements BoundedCacheStore<K, V> {

    /** 永不过期标记 */
    private static final long IMMORTAL = 0L;

    /** 窗口区占比 1%（至少 1） */
    private static final int WINDOW_PERCENT = 1;
    /** 主区中 probation 段占比 80% */
    private static final int PROBATION_PERCENT = 80;

    // region 取值
    private static final int R_WINDOW = 0;
    private static final int R_PROBATION = 1;
    private static final int R_PROTECTED = 2;
    private static final int R_DETACHED = -1; // 已从所有分段摘除

    // ---- 内部条目 ----

    static final class InternalEntry<K, V> {
        final K key;
        volatile V value;
        volatile long expiryMs; // 0 = 永不过期, > 0 = 绝对到期时间戳（ms）
        /** 当前所在区域，只在持有对应分段锁时读写 */
        volatile int region;

        InternalEntry(K key, V value, long expiryMs, int region) {
            this.key = key;
            this.value = value;
            this.expiryMs = expiryMs;
            this.region = region;
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

    // ---- 字段 ----

    private final ConcurrentHashMap<K, InternalEntry<K, V>> map = new ConcurrentHashMap<>();

    /** 窗口 LRU（accessOrder）：tail 为最近使用 */
    private final LinkedHashMap<K, InternalEntry<K, V>> window = new LinkedHashMap<>(16, 0.75f, true);
    /** 主区 probation 段 LRU */
    private final LinkedHashMap<K, InternalEntry<K, V>> probation = new LinkedHashMap<>(16, 0.75f, true);
    /** 主区 protected 段 LRU */
    private final LinkedHashMap<K, InternalEntry<K, V>> protectedSeg = new LinkedHashMap<>(16, 0.75f, true);

    private final ReentrantLock windowLock = new ReentrantLock();
    private final ReentrantLock probationLock = new ReentrantLock();
    private final ReentrantLock protectedLock = new ReentrantLock();
    private final Object sketchLock = new Object();

    private final FrequencySketch sketch;
    private final AtomicBoolean evicting = new AtomicBoolean();
    private final Map<CacheEventType, List<CacheEventListener<? super K, ? super V>>> listeners
            = new ConcurrentHashMap<>();

    private final long capacity;
    private final int windowMax;
    private final int mainMax;
    private final int probationMax;

    // ========== 构造器 ==========

    public MemoryCache() {
        this(-1);
    }

    public MemoryCache(long capacity) {
        this.capacity = capacity;
        long cap = Math.max(1, capacity);
        this.windowMax = (int) Math.max(1, cap * WINDOW_PERCENT / 100);
        this.mainMax = (int) Math.max(1, cap - windowMax);
        this.probationMax = (int) Math.max(1, mainMax * PROBATION_PERCENT / 100);
        this.sketch = new FrequencySketch(cap);
    }

    // ========== 内部辅助 ==========

    private boolean isExpired(InternalEntry<K, V> entry) {
        return entry.expiryMs != IMMORTAL && System.currentTimeMillis() >= entry.expiryMs;
    }

    private InternalEntry<K, V> getEntry(K key) {
        InternalEntry<K, V> entry = map.get(key);
        if (entry == null) return null;
        if (isExpired(entry)) {
            if (map.remove(key, entry)) {
                unlink(entry);
                fireEvent(CacheEventType.EXPIRE, key, entry.value);
                fireEvent(CacheEventType.INVALIDATE, key, entry.value);
            }
            return null;
        }
        return entry;
    }

    /**
     * 从条目当前所属的分段中摘除（仅索引，不删主表）。
     * region 读取与分段操作在同一把锁内完成，保证一致性。
     */
    private void unlink(InternalEntry<K, V> entry) {
        for (;;) {
            switch (entry.region) {
                case R_WINDOW -> {
                    windowLock.lock();
                    try {
                        if (entry.region != R_WINDOW) continue; // 并发迁移了，重读
                        window.remove(entry.key, entry);
                        entry.region = R_DETACHED;
                    } finally { windowLock.unlock(); }
                    return;
                }
                case R_PROBATION -> {
                    probationLock.lock();
                    try {
                        if (entry.region != R_PROBATION) continue;
                        probation.remove(entry.key, entry);
                        entry.region = R_DETACHED;
                    } finally { probationLock.unlock(); }
                    return;
                }
                case R_PROTECTED -> {
                    protectedLock.lock();
                    try {
                        if (entry.region != R_PROTECTED) continue;
                        protectedSeg.remove(entry.key, entry);
                        entry.region = R_DETACHED;
                    } finally { protectedLock.unlock(); }
                    return;
                }
                default -> { return; } // R_DETACHED
            }
        }
    }

    /** 记录一次访问（命中时调用）：更新频率估计 + 分段内晋升。 */
    private void onAccess(K key, InternalEntry<K, V> entry) {
        synchronized (sketchLock) {
            sketch.increment(key);
        }
        for (;;) {
            switch (entry.region) {
                case R_WINDOW -> {
                    windowLock.lock();
                    try {
                        if (entry.region != R_WINDOW) continue;
                        window.get(key); // LRU touch
                    } finally { windowLock.unlock(); }
                    return;
                }
                case R_PROBATION -> {
                    promoteToProtected(key, entry);
                    return;
                }
                case R_PROTECTED -> {
                    protectedLock.lock();
                    try {
                        if (entry.region != R_PROTECTED) continue;
                        protectedSeg.get(key);
                    } finally { protectedLock.unlock(); }
                    return;
                }
                default -> { return; } // 已被摘除（并发淘汰/删除），忽略
            }
        }
    }

    /**
     * probation 条目命中后晋升到 protected；protected 超容时 LRU 降级回 probation。
     * 锁顺序：probationLock → protectedLock（降级时再取 probationLock，
     * 与外层顺序一致，因为此刻外层 probationLock 已由本线程持有，可重入）。
     */
    private void promoteToProtected(K key, InternalEntry<K, V> entry) {
        probationLock.lock();
        try {
            if (entry.region != R_PROBATION) return; // 已被并发摘除/迁移
            probation.remove(key, entry);
            entry.region = R_PROTECTED;
            protectedLock.lock();
            try {
                protectedSeg.put(key, entry);
                while (protectedSeg.size() > mainMax - probationMax) {
                    InternalEntry<K, V> demoted = pollEldest(protectedSeg);
                    if (demoted == null) break;
                    demoted.region = R_PROBATION;
                    probation.put(demoted.key, demoted); // probationLock 可重入
                }
            } finally {
                protectedLock.unlock();
            }
        } finally {
            probationLock.unlock();
        }
    }

    /** 移除并返回 LRU 最老条目（调用方须持有对应锁）。 */
    private static <K, V> InternalEntry<K, V> pollEldest(LinkedHashMap<K, InternalEntry<K, V>> seg) {
        Iterator<Map.Entry<K, InternalEntry<K, V>>> it = seg.entrySet().iterator();
        if (!it.hasNext()) return null;
        InternalEntry<K, V> e = it.next().getValue();
        it.remove();
        return e;
    }

    /**
     * 从 probation 挑选主区淘汰受害者（LRU 最老）。
     * 返回的条目已从 probation 摘除且 region 置为 R_DETACHED，
     * 后续并发 unlink 不会重复触碰。
     */
    private InternalEntry<K, V> pickMainVictim() {
        probationLock.lock();
        try {
            Iterator<Map.Entry<K, InternalEntry<K, V>>> it = probation.entrySet().iterator();
            while (it.hasNext()) {
                InternalEntry<K, V> e = it.next().getValue();
                if (map.get(e.key) != e) { it.remove(); e.region = R_DETACHED; continue; } // 自愈
                it.remove();
                e.region = R_DETACHED;
                return e;
            }
            return null;
        } finally {
            probationLock.unlock();
        }
    }

    /**
     * 淘汰路径：window 满时挤出 LRU 候选，经 TinyLFU 准入判定后进入主区或被拒绝。
     */
    private void evictFromWindow() {
        InternalEntry<K, V> candidate;
        windowLock.lock();
        try {
            candidate = pollEldest(window);
            if (candidate != null) candidate.region = R_DETACHED; // 先置 DETACHED，杜绝幽灵
        } finally {
            windowLock.unlock();
        }
        if (candidate == null) return;
        // 主表可能已删除（惰性过期/并发 remove）
        if (map.get(candidate.key) != candidate) return;

        InternalEntry<K, V> victim = pickMainVictim();
        if (victim == null) {
            admit(candidate);
            return;
        }
        int candidateFreq, victimFreq;
        synchronized (sketchLock) {
            candidateFreq = sketch.estimate(candidate.key);
            victimFreq = sketch.estimate(victim.key);
        }
        if (candidateFreq > victimFreq) {
            // 候选更热：淘汰受害者，候选入主区
            if (map.remove(victim.key, victim)) {
                fireEvent(CacheEventType.EVICT, victim.key, victim.value);
                fireEvent(CacheEventType.INVALIDATE, victim.key, victim.value);
            }
            admit(candidate);
        } else {
            // 拒绝候选：从主表移除；若并发线程已修改/删除了映射，
            // 条目 region 已是 R_DETACHED，并发 unlink/onAccess 会安全忽略
            if (map.remove(candidate.key, candidate)) {
                fireEvent(CacheEventType.EVICT, candidate.key, candidate.value);
                fireEvent(CacheEventType.INVALIDATE, candidate.key, candidate.value);
            } else if (map.containsKey(candidate.key)) {
                // 并发 put 更新了主表条目——必须让它重新归位，否则成幽灵
                admit(candidate);
            }
        }
    }

    /** 候选通过准入判定，进入主区 probation 段。 */
    private void admit(InternalEntry<K, V> entry) {
        probationLock.lock();
        try {
            if (entry.region != R_DETACHED) return; // 已被并发安置，放弃
            entry.region = R_PROBATION;
            probation.put(entry.key, entry);
        } finally {
            probationLock.unlock();
        }
    }

    /**
     * 确保容量：清过期 → window 超额走 W-TinyLFU 淘汰。
     * try-lock 串行化；容量为软上限。
     */
    private void ensureCapacity() {
        if (capacity <= 0) return;
        gc();
        if (!evicting.compareAndSet(false, true)) return;
        try {
            while (windowSize() > windowMax && map.size() > capacity - windowMax) {
                evictFromWindow();
            }
            // 极端回退：map 仍超容时直接从 probation 尾部强删
            while (map.size() > capacity) {
                InternalEntry<K, V> victim = pickMainVictim();
                if (victim == null) break;
                if (map.remove(victim.key, victim)) {
                    fireEvent(CacheEventType.EVICT, victim.key, victim.value);
                    fireEvent(CacheEventType.INVALIDATE, victim.key, victim.value);
                }
            }
        } finally {
            evicting.set(false);
        }
    }

    private int windowSize() {
        windowLock.lock();
        try { return window.size(); } finally { windowLock.unlock(); }
    }

    private void addNew(K key, V value, long expiryMs) {
        InternalEntry<K, V> entry = new InternalEntry<>(key, value, expiryMs, R_WINDOW);
        map.put(key, entry);
        synchronized (sketchLock) {
            sketch.increment(key);
        }
        windowLock.lock();
        try {
            window.put(key, entry);
        } finally {
            windowLock.unlock();
        }
    }

    private static long expiryFromDuration(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return IMMORTAL;
        }
        return System.currentTimeMillis() + duration.toMillis();
    }

    // ========== CacheStore 写操作 ==========

    /**
     * 写入一个永不过期的缓存项。
     * <p>
     * 如果 key 已存在，覆盖原值并清除原有的 TTL。
     */
    @Override
    public void put(K key, V value) {
        putInternal(key, value, IMMORTAL);
    }

    /**
     * 原子写入一个永不过期的缓存项，仅当 key 不存在时生效。
     */
    @Override
    public boolean putIfAbsent(K key, V value) {
        return putIfAbsentInternal(key, value, IMMORTAL);
    }

    @Override
    public void put(K key, V value, Duration duration) {
        if (duration == null) {
            put(key, value);
            return;
        }
        if (duration.isZero() || duration.isNegative()) {
            remove(key);
            return;
        }
        putInternal(key, value, expiryFromDuration(duration));
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        if (duration == null) {
            return putIfAbsent(key, value);
        }
        if (duration.isZero() || duration.isNegative()) {
            return false;
        }
        return putIfAbsentInternal(key, value, expiryFromDuration(duration));
    }

    private void putInternal(K key, V value, long expiryMs) {
        InternalEntry<K, V> existing = getEntry(key);
        if (existing != null) {
            // computeIfPresent 原子更新，不复活已删除的映射
            InternalEntry<K, V> cur = map.computeIfPresent(key, (_, e) -> {
                e.value = value;
                e.expiryMs = expiryMs;
                return e;
            });
            if (cur != null) {
                onAccess(key, cur);
                fireEvent(CacheEventType.UPDATE, key, value);
                return;
            }
        }
        ensureCapacity();
        addNew(key, value, expiryMs);
        fireEvent(CacheEventType.CREATE, key, value);
    }

    private boolean putIfAbsentInternal(K key, V value, long expiryMs) {
        ensureCapacity();
        @SuppressWarnings("unchecked")
        InternalEntry<K, V>[] replaced = new InternalEntry[1];
        InternalEntry<K, V> result = map.compute(key, (_, existing) -> {
            if (existing != null && !isExpired(existing)) {
                return existing;
            }
            replaced[0] = existing;
            return new InternalEntry<>(key, value, expiryMs, R_WINDOW);
        });
        if (replaced[0] == null && result.value != value) {
            return false; // key 已存在且未过期
        }
        InternalEntry<K, V> old = replaced[0];
        if (old != null) {
            unlink(old);
            fireEvent(CacheEventType.EXPIRE, key, old.value);
            fireEvent(CacheEventType.INVALIDATE, key, old.value);
        }
        synchronized (sketchLock) {
            sketch.increment(key);
        }
        InternalEntry<K, V> cur = map.get(key);
        if (cur != null && cur.region == R_WINDOW) {
            windowLock.lock();
            try {
                if (cur.region == R_WINDOW) window.put(key, cur);
            } finally {
                windowLock.unlock();
            }
        }
        fireEvent(CacheEventType.CREATE, key, value);
        return true;
    }

    // ========== CacheStore 读取 ==========

    @Override
    public V get(K key) {
        InternalEntry<K, V> entry = getEntry(key);
        if (entry == null) {
            synchronized (sketchLock) { sketch.increment(key); } // 未命中也计数，利于突发识别
            return null;
        }
        onAccess(key, entry);
        return entry.value;
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(K key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            remove(key);
            return;
        }
        InternalEntry<K, V> entry = getEntry(key);
        if (entry != null) {
            entry.expiryMs = expiryFromDuration(duration);
        }
    }

    @Override
    public Duration ttl(K key) {
        InternalEntry<K, V> entry = getEntry(key);
        if (entry == null) return null;
        if (entry.expiryMs == IMMORTAL) return Duration.ZERO;
        long remaining = entry.expiryMs - System.currentTimeMillis();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    // ========== 删除 ==========

    @Override
    public void remove(K key) {
        InternalEntry<K, V> entry = map.remove(key);
        if (entry == null) return;
        unlink(entry);
        if (isExpired(entry)) {
            fireEvent(CacheEventType.EXPIRE, key, entry.value);
        } else {
            fireEvent(CacheEventType.REMOVE, key, entry.value);
        }
        fireEvent(CacheEventType.INVALIDATE, key, entry.value);
    }

    @Override
    public void clear() {
        map.clear();
        windowLock.lock();
        try { window.clear(); } finally { windowLock.unlock(); }
        probationLock.lock();
        try { probation.clear(); } finally { probationLock.unlock(); }
        protectedLock.lock();
        try { protectedSeg.clear(); } finally { protectedLock.unlock(); }
    }

    // ========== 查询 ==========

    @Override
    public long approxCount() {
        return map.mappingCount();
    }

    @Override
    public boolean containsKey(K key) {
        return getEntry(key) != null;
    }

    // ========== BoundedCacheStore ==========

    @Override
    public long gc() {
        long count = 0;
        for (Map.Entry<K, InternalEntry<K, V>> e : map.entrySet()) {
            InternalEntry<K, V> ie = e.getValue();
            if (isExpired(ie) && map.remove(e.getKey(), ie)) {
                unlink(ie);
                count++;
                fireEvent(CacheEventType.EXPIRE, e.getKey(), ie.value);
                fireEvent(CacheEventType.INVALIDATE, e.getKey(), ie.value);
            }
        }
        return count;
    }

    @Override
    public boolean isFull() {
        return capacity > 0 && map.size() >= capacity;
    }

    @Override
    public long capacity() {
        return capacity;
    }

    // ========== 事件监听器 ==========

    @Override
    public void addListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        if (type == null || listener == null) return;
        listeners.computeIfAbsent(type, _ -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void removeListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        if (type == null || listener == null) return;
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list != null) {
            list.remove(listener);
        }
    }

    @Override
    public void removeListeners(CacheEventType type) {
        if (type == null) return;
        listeners.remove(type);
    }

    private void fireEvent(CacheEventType type, K key, V value) {
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list != null) {
            for (CacheEventListener<? super K, ? super V> l : list) {
                l.onEvent(type, key, value);
            }
        }
    }
}
