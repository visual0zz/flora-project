package com.flora.cache;

/**
 * 缓存淘汰策略接口：依据 key 的读写通知决定淘汰谁。
 * <p>
 * 策略通过 {@code onPut}/{@code onGet}/{@code onTouch}/{@code onRemove} 接收 key 的事件通知，
 * 自行维护内部索引（如 LRU 链表、频率计数）。策略只<b>决策</b>淘汰谁，不触碰存储：
 * 引擎在需要淘汰时调用 {@link #selectEvictVictim()} 取回待删的 key，由引擎负责真正删除与派发事件。
 * <p>
 * 调用约定（由引擎 {@code CacheSupport} 保证）：
 * <ul>
 *   <li>写入（put / putIfAbsent，含覆盖写）→ {@link #onPut} + {@link #onTouch}</li>
 *   <li>读取（get，命中或未命中）→ {@link #onGet} + {@link #onTouch}</li>
 *   <li>删除 / 过期 → {@link #onRemove}（不再 touch）</li>
 * </ul>
 * 其中 {@code onPut} / {@code onGet} 描述「发生了哪种操作」并负责把 key 登记进策略，
 * {@code onTouch} 负责刷新该 key 的热度（频率 / 最近使用位置）。
 *
 * @param <K> 键类型
 * @param <V> 值类型（策略通常只基于 key 工作，V 仅为对称保留）
 */
public interface EvictionPolicy<K, V> {

    /**
     * 写入（put / putIfAbsent）后回调，用于把 key 登记进策略内部索引（首次写入即开始追踪）。
     * 不负责刷新热度——热度刷新由 {@link #onTouch} 承担。
     *
     * @param key 键
     */
    void onPut(K key);

    /**
     * 读取（get）后回调，用于记录一次读取事件。命中时 key 已登记在策略中；
     * 未命中时可用于需求预热（如累加频率素描），但不应把未驻留的 key 纳入淘汰候选段。
     *
     * @param key 键
     */
    void onGet(K key);

    /**
     * key 被引用（读取或写入）后回调，用于刷新其热度（频率计数 / 最近使用位置）。
     * 写入与读取都会触发，故每次对 key 的引用恰好刷新一次热度。
     *
     * @param key 键
     */
    void onTouch(K key);

    /**
     * 条目被显式删除或过期后回调，从候选集与索引中摘除。
     *
     * @param key 键
     */
    void onRemove(K key);

    /**
     * 选择并返回一个待淘汰的 key（仅决策，不删除存储）；当前无需淘汰（容量未满、
     * 或本步仅做了内部准入而无须删除存储）时返回 {@code null}。调用方负责删除该 key。
     * <p>
     * 注意：返回 {@code null} 不代表缓存已空，可能只是本步内部状态已推进（如候选被准入主区）。
     *
     * @return 待淘汰的 key，或 {@code null}
     */
    K selectEvictVictim();

    /**
     * 清空策略内部全部索引与统计（不触碰存储）。
     */
    void clear();
}
