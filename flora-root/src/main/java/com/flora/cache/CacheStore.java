package com.flora.cache;


import java.time.Duration;

/**
 * 缓存能力提供方接口。
 * <p>
 * 定义所有缓存后端（本地 HashMap、Redis、分布式等）必须实现的最小公共契约。
 * 有界/本地缓存特有能力（容量、gc、事件监听）见 {@link BoundedCacheStore}。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface CacheStore<K, V> {

    // ---- 写入 ----

    /**
     * 写入一个永不过期的缓存项。
     * <p>
     * 如果 key 已存在，覆盖原值并清除原有的 TTL。
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
     * <p>
     * {@code duration} 为 {@link Duration#ZERO} 或负数时，行为等价于 {@link #remove(Object)}。
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
     * <p>
     * 如果 key 不存在，行为由实现类决定（静默忽略或抛出异常）。
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
     */
    void remove(K key);

    /**
     * 清空所有缓存项。
     */
    void clear();

    // ---- 查询 ----

    /**
     * 返回当前缓存项数量的近似值。
     * <p>
     * 实现类可能返回精确值或估算值，调用方不应依赖精确性。
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
     * 判断指定 key 是否存在。
     *
     * @param key 键
     * @return {@code true} 表示 key 存在
     */
    boolean containsKey(K key);
}
