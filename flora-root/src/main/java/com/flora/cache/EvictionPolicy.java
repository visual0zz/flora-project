package com.flora.cache;

/**
 * 淘汰策略回调接口。
 * <p>
 * 缓存（{@link Cache} / {@link MemoryCache}）在每次访问与移除时回调本接口，
 * 使策略维护内部追踪状态（访问序、权重、频率等）并在容量超限时选出待回收的条目。
 * <p>
 * 设计上以两个枚举取代原先一组细碎的 {@code onXxx} 回调：
 * <ul>
 *   <li>写 / 读类操作统一通过 {@link #onAccess(Object, AccessAction, boolean)}，
 *       由 {@link AccessAction} 区分 PUT / GET / TOUCH；</li>
 *   <li>移除类操作统一通过 {@link #onRemove(Object, RemoveReason)}，
 *       由 {@link RemoveReason} 区分 EVICT / EXPIRE / REMOVE。</li>
 * </ul>
 * 各策略按自身需要 switch 枚举即可，无需为每种具体类型分别实现方法；感知不到
 * 细分类型差异的策略只需在 {@code onAccess} 里处理热度、在 {@code onRemove} 里摘除 key。
 * 语义分类与 {@link com.flora.cache.CacheEventType} 的「写 / 失效」聚合一一对应。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface EvictionPolicy<K, V> {

    /**
     * 通知策略：发生了一次访问 / 写操作。
     * <p>
     * 写（PUT）与命中读（GET 命中）都会刷新条目热度、影响淘汰排序；未命中读（GET 且
     * {@code existed == false}）通常仅预热频率素描、不进入淘汰候选段。策略据此维护内部
     * 追踪状态（访问序、权重、频率计数等）。
     *
     * @param key     被访问的键
     * @param action  访问类型（PUT / GET / TOUCH）
     * @param existed 操作前该 key 是否已逻辑存在；
     *                PUT 时 {@code false}=新建(INSERT)、{@code true}=覆盖(UPDATE)；
     *                GET 时 {@code false}=未命中；TOUCH 语义上恒为已存在态
     */
    void onAccess(K key, AccessAction action, boolean existed);

    /**
     * 通知策略：某 key 以指定原因离开缓存（被淘汰 / 过期 / 显式删除）。
     * <p>
     * 策略应在此将 key 从内部追踪结构（访问序、权重表、频率计数等）中摘除，保持状态与缓存一致。
     * 不同 {@link RemoveReason} 的内部处理通常一致；如需区分（统计 / 日志）可按枚举分别处理。
     *
     * @param key   被移除的键
     * @param reason 移除原因（EVICT / EXPIRE / REMOVE）
     */
    void onRemove(K key, RemoveReason reason);

    /**
     * 通知策略缓存被整体清空（clear）。
     * <p>
     * 策略应丢弃全部内部状态（访问序、权重表、频率计数等），恢复到初始空态。
     * 注意：整体清空的 CLEAR 事件以「全部失效」表示，不属于逐条 {@link #onRemove} 通知范畴。
     */
    void onClear();

    /**
     * 选出待淘汰的受害者键。
     * <p>
     * 当缓存超出容量、需要回收空间时调用。返回一个应当被移除的 key；若当前无需淘汰
     * （未满、无候选或无法选出）则返回 {@code null}。返回的 key 将被缓存移除，并触发
     * {@link RemoveReason#EVICT} 的 {@link #onRemove} 回调。
     * 这是淘汰策略的核心决策点。
     *
     * @return 待淘汰的 key，或 {@code null} 表示无需淘汰
     */
    K selectVictim();
}
