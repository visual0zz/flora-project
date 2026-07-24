package com.flora.cache;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 缓存契约：对外直接可用的缓存。
 * <p>
 * 一个 {@code CacheStore} 既是存储、也是对外的缓存门面——无论是否挂载
 * {@link EvictionPolicy} 插件，它都能直接服务：无插件时为无界缓存，
 * 挂上插件后自动获得容量约束与淘汰能力。调用方只依赖本接口。
 * <p>
 * <b>淘汰策略即插件</b>：{@link EvictionPolicy} 不是与存储平等组合的另一个零件，
 * 而是挂在 {@code CacheStore} 上的可选插件（{@link #setEvictionPolicy}）。
 * 因此不存在「缓存套缓存 / 反复叠加策略」的嵌套 —— 挂载点收的是策略对象，
 * 不是另一个 {@code CacheStore}，再挂一次只会替换插件。
 * <p>
 * 单体实现（把存储与策略焊死）同样直接实现本接口即可。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface CacheStore<K, V> {

    // ---- 写入 ----

    /**
     * 写入一个永不过期的缓存项。
     * <p>如果 key 已存在，覆盖原值并清除原有的 TTL。
     *
     * @param key   键
     * @param value 值，不允许为 {@code null}
     */
    void put(K key, V value);

    /**
     * 原子写入一个永不过期的缓存项，仅当 key 不存在时生效。
     *
     * @param key   键
     * @param value 值，不允许为 {@code null}
     * @return {@code true} 表示 key 原本不存在、写入成功；
     *         {@code false} 表示 key 已存在、未覆盖
     */
    boolean putIfAbsent(K key, V value);

    /**
     * 写入一个带 TTL 的缓存项。
     * <p>{@code duration} 为 {@link Duration#ZERO} 或负数时，行为等价于 {@link #remove(Object)}。
     *
     * @param key      键
     * @param value    值，不允许为 {@code null}
     * @param duration 过期时长，必须为正数
     */
    void put(K key, V value, Duration duration);

    /**
     * 原子写入一个带 TTL 的缓存项，仅当 key 不存在时生效。
     *
     * @param key      键
     * @param value    值，不允许为 {@code null}
     * @param duration 过期时长，必须为正数
     * @return {@code true} 表示 key 原本不存在、写入成功；
     *         {@code false} 表示 key 已存在、未覆盖
     */
    boolean putIfAbsent(K key, V value, Duration duration);

    // ---- 读取 ----

    /**
     * 获取缓存值。
     *
     * @param key 键
     * @return 缓存值；key 不存在返回 {@code null}
     */
    V get(K key);

    // ---- TTL 管理 ----

    /**
     * 设置或更新指定 key 的过期时长。
     * <p>如果 key 不存在，行为由实现类决定（静默忽略或抛出异常）。
     * {@code duration} 为 {@link Duration#ZERO} 或负数时，等价于 {@link #remove(Object)}。
     *
     * @param key      键
     * @param duration 过期时长
     */
    void setTtl(K key, Duration duration);

    /**
     * 查询指定 key 的剩余过期时间。
     *
     * @param key 键
     * @return 剩余过期时长；永不过期返回 {@link Duration#ZERO}；
     *         key 不存在返回 {@code null}
     */
    Duration ttl(K key);

    // ---- 删除 ----

    /**
     * 移除指定 key 的缓存项。
     *
     * @param key 键
     * @return 被移除的值；key 不存在返回 {@code null}
     */
    V remove(K key);

    /**
     * 清空所有缓存项。
     */
    void clear();

    // ---- 查询 ----

    /**
     * 返回当前缓存项数量的近似值。
     * <p>实现类可能返回精确值或估算值，调用方不应依赖精确性。
     *
     * @return 缓存项数量
     */
    long approxCount();

    /**
     * 缓存是否为空。
     */
    default boolean isEmpty() {
        return approxCount() == 0;
    }

    /**
     * 判断指定 key 是否存在（未过期）。
     *
     * @param key 键
     * @return {@code true} 表示 key 存在
     */
    boolean containsKey(K key);

    // ---- 组合层支撑（供内部 gc 扫描过期；默认空实现） ----

    /**
     * 返回当前所有 key 的快照视图，用于主动过期扫描（gc）。
     * <p>淘汰路径不依赖此方法（策略自管索引，O(1) 产出候选），
     * 仅在 {@code gc()} 等低频场景遍历。不支持主动过期的存储可忽略（默认空集合）。
     *
     * @return key 的可遍历视图
     */
    default Iterable<K> keys() {
        return Collections.emptySet();
    }

    /**
     * 判断指定 key 当前是否已过期（未过期或不存在返回 {@code false}）。
     * <p>存储无关的无 TTL 实现可忽略（默认 {@code false}）。
     *
     * @param key 键
     * @return 是否已过期
     */
    default boolean isExpired(K key) {
        return false;
    }

    // ---- 容量与回收 ----

    /**
     * 执行垃圾回收，清理过期或可淘汰的缓存项。
     *
     * @return 被清理的缓存项数量
     */
    long gc();

    /**
     * 缓存是否已满（容量上限处）。无界缓存（未挂载策略或容量为 0/负）恒为 {@code false}。
     *
     * @return {@code true} 表示当前缓存项数量已达到容量上限
     */
    boolean isFull();

    /**
     * 缓存容量上限。
     *
     * @return 最多能容纳的缓存项数量；{@code 0} 或负数表示无上限
     */
    long capacity();

    // ---- 事件监听 ----

    /**
     * 注册指定类型的缓存事件监听器。
     * <p>同一事件类型可添加多个监听器，按添加顺序依次回调；重复添加同一实例不去重。
     * 监听器内部抛异常不会影响缓存主流程，也不跳过同批次其他监听器。
     *
     * @param type     事件类型
     * @param listener 监听器；{@code null} 静默忽略
     */
    void addListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener);

    /** 移除指定类型的某个监听器（引用相等或 {@link Object#equals} 判断）。 */
    void removeListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener);

    /** 移除指定类型的所有监听器。 */
    void removeListeners(CacheEventType type);

    /** 批量添加监听器。 */
    default void addListeners(Map<CacheEventType, CacheEventListener<? super K, ? super V>> listeners) {
        if (listeners == null) return;
        listeners.forEach(this::addListener);
    }

    /** 清除所有事件类型的全部监听器。 */
    default void removeAllListeners() {
        for (CacheEventType type : CacheEventType.values()) {
            removeListeners(type);
        }
    }

    // ---- 淘汰策略插件（可选） ----

    /**
     * 挂载淘汰策略插件。挂上后即获得该策略的淘汰能力（受 {@link #capacity()} 约束）；
     * 传入 {@code null} 表示移除插件、恢复无界。重复挂载只会替换插件，不会嵌套。
     *
     * @param policy 淘汰策略插件，或 {@code null}
     */
    void setEvictionPolicy(EvictionPolicy<K, V> policy);

    /**
     * 返回当前挂载的淘汰策略插件；未挂载返回 {@code null}。
     *
     * @return 当前插件，或 {@code null}
     */
    EvictionPolicy<K, V> evictionPolicy();
}
