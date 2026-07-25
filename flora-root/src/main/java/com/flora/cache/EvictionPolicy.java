package com.flora.cache;

/**
 * 缓存淘汰策略接口：只做淘汰决策，不碰存储细节与事件监听。
 * <p>
 * 策略按 key 接收读写通知，自行维护全部索引（如 LRU 分段、频率素描），
 * 以 O(1) 产出待淘汰的 key。被淘汰的 key 由挂载它的 {@link BoundedCache}
 * （如 {@code MemoryCache} 经 {@link BoundedCache#setEvictionPolicy} 挂上本策略）
 * 负责从存储中删除并触发 {@code EVICT}/{@code INVALIDATE} 事件；策略本身不持有 value、
 * 不触发监听器，从而与具体存储实现完全解耦，可自由组合。
 * <p>
 * 单体缓存（把存储与策略焊死的实现）可继承 {@code com.flora.cache.store.BoundedCacheSupport}
 * （或自行实现 {@link BoundedCache}）；其内部复用某个 {@link EvictionPolicy} 实现。
 *
 * @param <K> 键类型
 * @param <V> 值类型（策略通常不感知具体值，仅为与 {@link Cache} 对称保留）
 */
public interface EvictionPolicy<K, V> {

    /**
     * 新条目写入 / 覆盖写入后回调，用于把 key 加入候选集、记录初始频率。
     *
     * @param key 键
     */
    void onPut(K key);

    /**
     * 命中读取后回调（含未命中也调用，便于识别突发流量），用于更新频率与分段位置。
     *
     * @param key 键
     */
    void onAccess(K key);

    /**
     * 条目被显式删除或过期后回调，从候选集与索引中摘除。
     *
     * @param key 键
     */
    void onRemove(K key);

    /**
     * 执行一次淘汰步骤，返回待淘汰的 key；当前无需淘汰（容量未满或无可淘汰项）
     * 时返回 {@code null}。调用方负责从存储删除该 key 并触发事件。
     *
     * @return 待淘汰的 key，或 {@code null}
     */
    K evict();

    /**
     * 清空策略内部全部索引与统计（不触碰存储）。
     */
    void clear();
}
