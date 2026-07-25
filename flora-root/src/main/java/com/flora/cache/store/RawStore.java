package com.flora.cache.store;

import java.time.Duration;

/**
 * 原始存储 SPI：缓存引擎 {@link CacheEngine} 与具体 KV/TTL 存储之间的契约。
 * <p>
 * 实现者（如 {@link MemoryCache}、{@link RemoteCache}）只负责「真正的数据读写」，
 * 不承载任何淘汰、事件、过期语义判断——零/负时长、过期判定等统一由引擎处理。
 * 引擎在写入前已保证 {@code duration} 为正数（零/负已改走过期删除管线），故 {@code rawXxx}
 * 带 {@code Duration} 的重载无需再次校验时长合法性。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface RawStore<K, V> {

    /** 覆盖写入（永不过期）。 */
    void rawPut(K key, V value);

    /** 覆盖写入（带 TTL，{@code duration} 已由引擎保证为正数）。 */
    void rawPut(K key, V value, Duration duration);

    /** 原子写入，返回是否写入成功（仅当 key 不存在）。 */
    boolean rawPutIfAbsent(K key, V value);

    /** 原子写入（带 TTL，{@code duration} 已由引擎保证为正数），返回是否写入成功。 */
    boolean rawPutIfAbsent(K key, V value, Duration duration);

    /** 读取值；不存在返回 {@code null}（过期值由实现决定隐藏与否，引擎统一做过期处理）。 */
    V rawGet(K key);

    /** 删除并返回旧值；不存在返回 {@code null}。 */
    V rawRemove(K key);

    /** 是否存在且未过期（用于写时分支判断与 {@link com.flora.cache.Cache#containsKey}）。 */
    boolean rawContains(K key);

    /** 剩余过期时长；不存在返回 {@code null}，永不过期返回 {@link Duration#ZERO}。 */
    Duration rawTtl(K key);

    /** 设置/更新过期时间（key 不存在由实现决定行为）。 */
    void rawSetTtl(K key, Duration duration);

    /** 清空全部。 */
    void rawClear();

    /** 所有 key 的快照（供 {@code cleanUp} 扫描）。 */
    Iterable<K> rawKeys();

    /** 指定 key 是否已过期（未过期或不存在返回 {@code false}）。 */
    boolean rawIsExpired(K key);

    /** 当前条目数量近似值。 */
    long rawCount();
}
