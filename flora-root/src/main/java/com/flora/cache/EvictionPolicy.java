package com.flora.cache;

/**
 * 淘汰策略回调接口。
 * <p>
 * 缓存（{@link Cache} / {@link MemoryCache}）在每次访问与移除时回调本接口，
 * 使策略维护内部追踪状态（访问序、权重、频率等）并在容量超限时选出待回收的条目。
 * <p>
 * 设计上以两个回调取代原先一组细碎的 {@code onXxx} 回调，动作类型统一用
 * {@link CacheEventType} 表示（与对外事件类型一致）：
 * <ul>
 *   <li>写 / 读类操作统一通过 {@link #onAccess(Object, CacheEventType, boolean, Object, Object)}，
 *       由 {@link CacheEventType} 区分 PUT / GET / SET_TTL / GET_TTL；</li>
 *   <li>移除类操作统一通过 {@link #onRemove(Object, Object, CacheEventType)}，
 *       由 {@link CacheEventType} 区分 EVICT / EXPIRE / REMOVE。</li>
 * </ul>
 * 各策略按自身需要 switch 枚举即可，无需为每种具体类型分别实现方法；感知不到
 * 细分类型差异的策略只需在 {@code onAccess} 里处理热度、在 {@code onRemove} 里摘除 key。
 * 语义分类与 {@link CacheEventType} 的写 / 失效分类一一对应。
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
     * @param key      被访问的键
     * @param action   访问类型（PUT / GET / SET_TTL / GET_TTL）
     * @param existed  操作前该 key 是否已逻辑存在；
     *                 PUT 时 {@code false}=新建、{@code true}=覆盖写；
     *                 GET / SET_TTL / GET_TTL 语义上恒为已存在态
     * @param oldValue 本次操作覆盖前的旧值：PUT 覆盖写时为被替换的前值；PUT 新建、
     *                 GET、SET_TTL（不改写值）为 {@code null}。自定义策略如需在覆盖写时
     *                 回收被替换的旧值可借此使用
     * @param newValue 本次操作写入的新值：仅 PUT / PUT_IF_ABSENT 为写入的值；GET（读操作）
     *                 与 SET_TTL（仅刷新 TTL、不改写值）均为 {@code null}。策略通常不需使用，
     *                 仅为与 {@link #onRemove} 对称、便于需要值语义的自定义策略使用
     */
    void onAccess(K key, CacheEventType action, boolean existed, V oldValue, V newValue);

    /**
     * 通知策略：某 key 以指定原因离开缓存（被淘汰 / 过期 / 显式删除）。
     * <p>
     * 策略应在此将 key 从内部追踪结构（访问序、权重表、频率计数等）中摘除，保持状态与缓存一致。
     * 不同 {@link CacheEventType} 的内部处理通常一致；如需区分（统计 / 日志）可按枚举分别处理。
     * 若策略需在条目离开缓存时对其值执行资源回收（如关闭句柄），可在此用 {@code oldValue} 完成。
     *
     * @param key      被移除的键
     * @param oldValue 被移除前的值（EVICT / EXPIRE / REMOVE 均为离开缓存时的真实值）；
     *                 自定义策略可借此对其调用清理函数（如 {@code AutoCloseable.close()}）
     * @param reason   移除原因（CacheEventType#EVICT / EXPIRE / REMOVE）
     */
    void onRemove(K key, V oldValue, CacheEventType reason);

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
     * {@link CacheEventType#EVICT} 的 {@link #onRemove} 回调。
     * 这是淘汰策略的核心决策点。
     *
     * @return 待淘汰的 key，或 {@code null} 表示无需淘汰
     */
    K selectVictim();
}
