package com.flora.cache;

/**
 * 缓存淘汰策略接口：依据 key 的访问通知决定淘汰谁。
 * <p>
 * 策略通过 {@code onPut}/{@code onAccess}/{@code onRemove} 接收 key 的读写通知，
 * 自行维护内部索引（如 LRU 链表、频率计数），并通过 {@code evict()} 返回待淘汰的 key。
 *
 * @param <K> 键类型
 * @param <V> 值类型（策略通常只基于 key 工作，V 仅为对称保留）
 */
public interface EvictionPolicy<K, V> {

    /**
     * 新条目写入 / 覆盖写入后回调，用于把 key 加入候选集、记录初始频率。
     *
     * @param key 键
     */
    void onPut(K key);

    /**
     * 命中读取后回调（未命中也会回调，用于统计访问），用于更新频率与分段位置。
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
     * 执行一次淘汰步骤，返回待淘汰的 key；当前无需淘汰（容量未满或无可淘汰项）时返回 {@code null}。
     * 调用方负责删除该 key。
     *
     * @return 待淘汰的 key，或 {@code null}
     */
    K evict();

    /**
     * 清空策略内部全部索引与统计（不触碰存储）。
     */
    void clear();
}
