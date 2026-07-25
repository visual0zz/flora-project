package com.flora.cache;

/**
 * 有界缓存契约：提供容量约束与回收能力（容量上限、是否已满、垃圾回收）。
 * <p>
 * {@link #capacity()} 决定何时触发淘汰，淘汰策略（{@link EvictionPolicy}）决定淘汰谁。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface BoundedCache<K, V> extends EvictableCache<K, V> {

    /** 执行垃圾回收，清理过期或可淘汰的缓存项；返回被清理的数量。 */
    long gc();

    /** 是否已到达容量上限。 */
    boolean isFull();

    /** 容量上限；{@code 0} 或负数表示无上限。 */
    long capacity();

    /**
     * 挂载淘汰策略插件。挂上后即获得该策略的淘汰能力（受 {@link #capacity()} 约束）；
     * 传入 {@code null} 表示移除插件。重复挂载只会替换插件，不会嵌套。
     *
     * @param policy 淘汰策略插件，或 {@code null}
     */
    void setEvictionPolicy(EvictionPolicy<K, V> policy);

    /** 返回当前挂载的淘汰策略插件；未挂载返回 {@code null}。 */
    EvictionPolicy<K, V> evictionPolicy();
}
