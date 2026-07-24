package com.flora.cache;

import java.time.Duration;
import java.util.Collections;

/**
 * 缓存存储契约（最瘦）：定义所有缓存后端必须实现的最小公共存储能力——KV 与 TTL。
 * <p>
 * 这是三层接口的底座。其上按层叠加缓存行为：
 * <ul>
 *   <li>{@link ObservableCacheStore}：在存储之上叠加事件监听（可观测）；</li>
 *   <li>{@link BoundedCacheStore}：在可观测之上叠加尺寸限制与淘汰策略插件（有界）。</li>
 * </ul>
 * 把容量、事件、策略留在上层，本接口只谈数据，保持最小、最易被各种后端实现。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface CacheStore<K, V> {

    // ---- 写入 ----

    /** 写入永不过期的项；key 已存在则覆盖并清除原 TTL。 */
    void put(K key, V value);

    /** 原子写入永不过期项，仅当 key 不存在时生效；返回是否写入成功。 */
    boolean putIfAbsent(K key, V value);

    /** 写入带 TTL 的项；{@code duration} 为 0 或负时等价于 {@link #remove(Object)}。 */
    void put(K key, V value, Duration duration);

    /** 原子写入带 TTL 的项，仅当 key 不存在时生效；返回是否写入成功。 */
    boolean putIfAbsent(K key, V value, Duration duration);

    // ---- 读取 ----

    /** 获取缓存值；不存在返回 {@code null}。 */
    V get(K key);

    // ---- TTL 管理 ----

    /** 设置/更新 key 的过期时长；key 不存在由实现决定（静默忽略或抛异常）。 */
    void setTtl(K key, Duration duration);

    /** 查询 key 剩余过期时间；永不过期返回 {@link Duration#ZERO}，不存在返回 {@code null}。 */
    Duration ttl(K key);

    // ---- 删除 ----

    /** 移除 key；返回被移除的值，不存在返回 {@code null}。 */
    V remove(K key);

    /** 清空所有缓存项。 */
    void clear();

    // ---- 查询 ----

    /** 当前条目数量的近似值（允许精确或估算）。 */
    long approxCount();

    /** 缓存是否为空。 */
    default boolean isEmpty() {
        return approxCount() == 0;
    }

    /** key 是否存在（未过期）。 */
    boolean containsKey(K key);

    // ---- 过期扫描支撑（供内部 gc 使用，默认空实现） ----

    /** 返回所有 key 的快照视图，供主动过期扫描（gc）。不支持主动过期的存储可忽略。 */
    default Iterable<K> keys() {
        return Collections.emptySet();
    }

    /** 指定 key 当前是否已过期（未过期或不存在返回 {@code false}）。 */
    default boolean isExpired(K key) {
        return false;
    }
}
