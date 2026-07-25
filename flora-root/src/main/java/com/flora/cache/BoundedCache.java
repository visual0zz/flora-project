package com.flora.cache;

/**
 * 有界缓存契约：在 {@link EvictableCache}（存储 + 可挂策略）之上叠加尺寸限制。
 * <p>
 * 容量（{@link #capacity()}）决定「何时淘汰」，淘汰策略（{@link EvictionPolicy}）决定
 * 「淘汰谁」——二者正交：「能挂策略」与「有硬容量」是两条独立的能力轴。本接口把二者合并为
 * 「有界」这一层，因为<b>淘汰真正发生</b>需要尺寸约束与策略同时就位；但只有其一时仍是合法类型
 * （无界但挂策略 = 仅统计 / 准入；有界但未挂策略 = 不淘汰，容量只是上限标记）。
 * <p>
 * 事件监听（{@link ObservableCache}）与本接口<b>正交</b>，不再有 is-a 关系：一个缓存可以
 * 有界但不可观测，也可以可观测但无界。
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
     * 传入 {@code null} 表示移除插件、恢复无界。重复挂载只会替换插件，不会嵌套。
     *
     * @param policy 淘汰策略插件，或 {@code null}
     */
    void setEvictionPolicy(EvictionPolicy<K, V> policy);

    /** 返回当前挂载的淘汰策略插件；未挂载返回 {@code null}。 */
    EvictionPolicy<K, V> evictionPolicy();
}
