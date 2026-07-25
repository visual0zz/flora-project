package com.flora.cache;

/**
 * 缓存淘汰策略接口：依据 key 的读写通知决定淘汰谁。
 * <p>
 * 策略通过 {@code onPut}/{@code onGet}/{@code onTouch} 接收 key 的事件通知，
 * 自行维护内部索引。策略只<b>决策</b>淘汰谁，不触碰存储：
 * 引擎在需要淘汰时调用 {@link #selectEvictVictim()} 取回待删的 key，由引擎负责真正删除与派发事件。
 * <p>
 * 调用约定（由缓存实现类保证）：
 * <ul>
 *   <li>写入（put / putIfAbsent，含覆盖写）→ {@link #onPut} + {@link #onTouch}</li>
 *   <li>读取（get，命中或未命中）→ {@link #onGet} + {@link #onTouch}</li>
 *   <li>任意删除（显式 / 淘汰 / 过期）→ {@link #onRemove}（不再 touch）</li>
 * </ul>
 * 其中 {@code onPut} / {@code onGet} 描述「发生了哪种操作」并负责把 key 登记进策略，
 * {@code onTouch} 负责刷新该 key 的热度（频率 / 最近使用位置）。
 * 三个回调都携带 {@code existed} 参数，表示<b>本次操作发生前</b>该 key 是否已存在于存储中，
 * 供策略区分新插入 / 覆盖写 / 命中 / 未命中。
 * <p>
 * 删除事件另按来源分别回调 {@link #onExplicitRemove}/{@link #onEvict}/{@link #onExpire}，
 * 供策略针对显式删除、容量淘汰、TTL 过期做差异化处理（默认空实现；多数策略只需
 * {@link #onRemove} 即可）。
 *
 * @param <K> 键类型
 * @param <V> 值类型（策略通常只基于 key 工作，V 仅为对称保留）
 */
public interface EvictionPolicy<K, V> {

    /**
     * 写入（put / putIfAbsent）后回调，用于把 key 登记进策略内部索引（首次写入即开始追踪）。
     * 不负责刷新热度——热度刷新由 {@link #onTouch(Object, boolean)} 承担。
     *
     * @param key     键
     * @param existed 本次写入前该 key 是否已存在于存储中（{@code true}=覆盖写，{@code false}=新插入）
     */
    void onPut(K key, boolean existed);

    /**
     * 读取（get）后回调，用于记录一次读取事件。命中时 key 已登记在策略中；
     * 未命中时策略通常只做需求预热，但不应把未驻留的 key 纳入淘汰候选段。
     *
     * @param key     键
     * @param existed 本次读取前该 key 是否已存在于存储中（{@code true}=命中，{@code false}=未命中）
     */
    void onGet(K key, boolean existed);

    /**
     * key 被引用（读取或写入）后回调，用于刷新其热度。
     * 写入与读取都会触发，故每次对 key 的引用恰好刷新一次热度。
     *
     * @param key     键
     * @param existed 本次操作前该 key 是否已存在于存储中；未驻留时策略通常只做需求预热，
     *                不应把该 key 纳入淘汰候选段
     */
    void onTouch(K key, boolean existed);

    /**
     * 条目被移除（显式删除 / 容量淘汰 / TTL 过期）后回调，从候选集与索引中摘除。
     * 每次删除都会触发，与来源无关；如需按来源差异化处理，见
     * {@link #onExplicitRemove}/{@link #onEvict}/{@link #onExpire}。
     *
     * @param key 键
     */
    void onRemove(K key);

    /**
     * 条目被显式 {@code remove} 删除后回调（可选钩子）。默认空实现；
     * 策略通常已在 {@link #onRemove} 中摘除，无需覆写，除非要对显式删除做特殊记账。
     *
     * @param key 键
     */
    default void onExplicitRemove(K key) {
    }

    /**
     * 条目因容量超限被淘汰后回调（可选钩子）。默认空实现；
     * 默认由 {@link #onRemove} 完成索引摘除，如需对淘汰做额外统计可覆写。
     *
     * @param key 键
     */
    default void onEvict(K key) {
    }

    /**
     * 条目因 TTL 过期被删除后回调（可选钩子）。默认空实现；
     * 默认由 {@link #onRemove} 完成索引摘除，如需对过期做额外统计可覆写。
     *
     * @param key 键
     */
    default void onExpire(K key) {
    }

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
