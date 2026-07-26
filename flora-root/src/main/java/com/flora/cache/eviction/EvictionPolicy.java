package com.flora.cache.eviction;

import com.flora.cache.Cache;
import com.flora.cache.CacheEventType;
import com.flora.cache.MemoryCache;

/**
 * 淘汰策略回调接口。
 * <p>
 * 缓存（{@link Cache} / {@link MemoryCache}）在每次读写与失效操作时回调本接口，
 * 使策略维护内部追踪状态（访问序、权重、频率等）并在容量超限时选出待回收的条目。
 * 各回调的语义与 {@link CacheEventType} 的「写 / 失效」分类一一对应：
 * <ul>
 *   <li>写操作（MUTATE 聚合）：{@link #onPut}（INSERT/UPDATE）、{@link #onTouch}（TOUCH）、
 *       {@link #onMutate}（MUTATE 统称）；</li>
 *   <li>失效（INVALIDATE 聚合）：{@link #onRemove}、{@link #onEvict}、{@link #onExpire}，
 *       以及它们的总括 {@link #onInvalidate}；</li>
 *   <li>无副作用的读：{@link #onGet}。</li>
 * </ul>
 * 缓存在派发具体类型事件（如 EVICT/EXPIRE/REMOVE）的同时，也会一并派发其聚合事件
 * （MUTATE / INVALIDATE）。因此策略实现通常只需处理聚合方法（{@link #onMutate} /
 * {@link #onInvalidate}）即可覆盖全部写 / 失效场景；仅在需要区分具体类型时才覆盖对应的
 * 具体方法。具体类型方法的默认实现为空，覆盖与否不影响聚合方法的派发。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface EvictionPolicy<K, V> {

    /**
     * 通知策略：一个键值对被写入（put / putIfAbsent 成功写入）。
     * <p>
     * 属于「写操作」聚合 {MUTATE}：{@code existed == false} 对应首次写入新键（{@link CacheEventType#INSERT}），
     * {@code existed == true} 对应覆盖已存在的键（{@link CacheEventType#UPDATE}）。
     * 策略通常在此把 key 记入访问序或更新其权重 / 频率计数。
     *
     * @param key     被写入的键
     * @param existed 写入前该 key 是否已存在；{@code false} = 新建，{@code true} = 覆盖更新
     */
    void onPut(K key, boolean existed);

    /**
     * 通知策略：发生了一次读取（get）。
     * <p>
     * 这是无副作用的读操作，既不触发写也不触发失效。命中（{@code existed == true}）时策略通常刷新
     * key 的「最近使用」状态（如 LRU 移到 MRU 端）；未命中（{@code existed == false}）时通常无操作。
     *
     * @param key     被读取的键
     * @param existed 读取时该 key 是否存在；{@code false} 表示未命中，策略一般无需处理
     */
    void onGet(K key, boolean existed);

    /**
     * 通知策略：某 key 的 TTL 被刷新（setTtl）。
     * <p>
     * 属于「写操作」聚合 {MUTATE}，对应 {TOUCH} 类型。策略通常将其视为一次访问 / 使用，
     * 刷新 key 的「最近使用」状态（与 {@link #onGet} 命中的效果类似），而不改变条目的价值度量。
     *
     * @param key     被刷新 TTL 的键
     * @param existed 刷新时该 key 是否存在
     */
    void onTouch(K key, boolean existed);

    /**
     * 通知策略：发生了任意写操作（"MUTATE" 聚合），即 INSERT / UPDATE / TOUCH 的统称。
     * <p>
     * 当缓存无法区分具体写类型、或策略希望统一处理全部写场景时调用本方法。
     * 与 {@link #onPut} / {@link #onTouch} 二者在语义上重叠——只要实现了本方法，通常无需再
     * 单独处理具体写类型。策略一般在此刷新 key 的「最近使用」状态。
     *
     * @param key     被写入 / 刷新的键
     * @param existed 操作前该 key 是否已存在
     */
    void onMutate(K key, boolean existed);

    void onAccess(K key, boolean existed);

    /**
     * 通知策略：某 key 被显式删除（remove）。
     * <p>
     * 属于「失效」聚合 {INVALIDATE}，对应 {REMOVE} 类型（区别于因容量触发的 EVICT 与 TTL 触发的 EXPIRE）。
     * 默认空实现；如需与 {@link #onInvalidate} 区分处理可覆盖本方法。
     *
     * @param key 被显式删除的键
     */
    default void onRemove(K key) {
    }

    /**
     * 通知策略：某 key 因本策略选中的淘汰而被移除（{@link #selectVictim()} 的返回值被真正淘汰）。
     * <p>
     * 属于「失效」聚合 {INVALIDATE}，对应 {EVICT} 类型。默认空实现；多数实现只需实现
     * {@link #onInvalidate} 即可覆盖全部失效场景，无需单独覆盖本方法。
     *
     * @param key 被淘汰移除的键
     */
    default void onEvict(K key) {
    }

    /**
     * 通知策略：某 key 因 TTL 过期被自动清理。
     * <p>
     * 属于「失效」聚合 {INVALIDATE}，对应 {EXPIRE} 类型（区别于显式 REMOVE 与容量 EVICT）。
     * 默认空实现；多数实现只需实现 {@link #onInvalidate} 即可。
     *
     * @param key 因过期被清理的键
     */
    default void onExpire(K key) {
    }

    /**
     * 通知策略：任意失效事件（"INVALIDATE" 聚合），即 EVICT / EXPIRE / REMOVE 的统称。
     * <p>
     * 当某个条目以任何方式离开缓存（被淘汰、过期或显式删除）时调用，具体类型事件与该聚合事件会一并派发。
     * 策略应在此将 key 从内部追踪结构（访问序、权重表等）中移除，以保持状态与缓存一致。
     * 多数实现只需实现本方法即可覆盖全部失效场景。
     *
     * @param key 失效（被移出缓存）的键
     */
    void onInvalidate(K key);

    /**
     * 通知策略缓存被整体清空（clear）。
     * <p>
     * 策略应丢弃全部内部状态（访问序、权重表、频率计数等），恢复到初始空态。
     * 注意：整体清空的 CLEAR 事件以 {@code key == null} 表示「全部失效」，不属于逐条
     * {INVALIDATE} 聚合族，故通过本方法而非 {@link #onInvalidate} 通知。
     */
    void onClear();
    /**
     * 选出待淘汰的受害者键。
     * <p>
     * 当缓存超出容量、需要回收空间时调用。返回一个应当被移除的 key；若当前无需淘汰
     * （未满、无候选或无法选出）则返回 {@code null}。返回的 key 将被缓存移除，并触发
     * {EVICT} / {INVALIDATE} 事件（进而回调 {@link #onEvict} / {@link #onInvalidate}）。
     * 这是淘汰策略的核心决策点。
     *
     * @return 待淘汰的 key，或 {@code null} 表示无需淘汰
     */
    K selectVictim();

}
