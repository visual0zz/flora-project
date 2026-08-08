package com.flora.cache.interfaces;

/**
 * 有界缓存契约：提供容量约束与回收能力（容量上限、是否已满、垃圾回收）。
 * <p>
 * {@link #capacity()} 决定何时触发淘汰，淘汰策略（{@link EvictionPolicy}）决定淘汰谁。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface BoundedCache<K, V> extends Cache<K, V> {

    /** 执行清理回收，清除过期或可淘汰的缓存项；返回被清理的数量。 */
    long cleanUp();

    /** 是否已到达容量上限。 */
    boolean isFull();

    /** 容量上限；{@code 0} 或负数表示无上限。 */
    long capacity();
}
