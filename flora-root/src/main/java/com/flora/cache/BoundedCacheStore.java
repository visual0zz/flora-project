package com.flora.cache;

/**
 * 有界缓存契约：在 {@link ObservableCacheStore}（存储 + 事件）之上叠加尺寸限制，
 * 并挂载淘汰策略插件。
 * <p>
 * 容量（{@link #capacity()}）决定「何时淘汰」，淘汰策略（{@link EvictionPolicy}）决定
 * 「淘汰谁」——二者正交，合起来才构成一个会真正淘汰的有界缓存。策略挂在哪一层、容量
 * 挂在哪一层，正是这套接口分层要澄清的：策略与尺寸同属「有界」这一层，因为淘汰只在
 * 有尺寸约束时才有意义。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface BoundedCacheStore<K, V> extends ObservableCacheStore<K, V> {

    /** 执行垃圾回收，清理过期或可淘汰的缓存项；返回被清理的数量。 */
    long gc();

    /** 是否已到达容量上限。 */
    boolean isFull();

    /** 容量上限；{@code 0} 或负数表示无上限。 */
    long capacity();

    /**
     * 挂载淘汰策略插件。挂上后即获得该策略的淘汰能力（受 {@link #capacity()} 约束）；
     * 传入 {@code null} 表示移除插件、恢复无界。重复挂载只会替换插件，不会嵌套。
     *
     * @param policy 淘汰策略插件，或 {@code null}
     */
    void setEvictionPolicy(EvictionPolicy<K, V> policy);

    /** 返回当前挂载的淘汰策略插件；未挂载返回 {@code null}。 */
    EvictionPolicy<K, V> evictionPolicy();
}
