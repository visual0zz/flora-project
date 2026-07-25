package com.flora.cache;

import java.time.Duration;

/**
 * 缓存存储契约：定义 KV 读写、TTL 与基础查询的最小公共能力。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface Cache<K, V> {

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

    /** key 是否存在（未过期）。 */
    boolean containsKey(K key);

    // ---- TTL 管理 ----

    /** 设置/更新 key 的过期时长；key 不存在由实现决定（静默忽略或抛异常）。 */
    void setTtl(K key, Duration duration);

    /** 查询 key 剩余过期时间；永不过期返回 {@link Duration#MAX}，不存在或已过期返回 {@link Duration#ZERO}。 */
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

}
