package com.flora.cache;

/**
 * 可配置驱逐策略的缓存契约：在 {@link CacheStore}（纯存储）之上叠加「挂载 / 卸除淘汰策略插件」的能力。
 * <p>
 * 与 {@link BoundedCacheStore}（= 本接口 + 容量约束）正交：「能挂策略」与「有硬容量」是两件事——
 * 一个<b>无界</b>缓存也可以挂策略（仅做统计 / 准入，不触发删除，因为淘汰由
 * {@link EvictionPolicy#evict()} 在容量未超限时返回 {@code null} 决定）。这把原先只在运行时用
 * {@code capacity > 0} 表达的语义，正式提升到了类型系统层面：策略回调的唤醒闸门也相应从
 * {@code capacity > 0} 放宽为「策略已挂载（{@code policy != null}）」，使无界但挂了策略的缓存
 * 也能向策略喂数据。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface EvictionConfigableCacheStore<K, V> extends CacheStore<K, V> {

    /**
     * 挂载淘汰策略插件。挂上后即获得该策略的淘汰 / 统计能力；传入 {@code null} 表示移除插件、恢复无界。
     * 重复挂载只会替换插件，不会嵌套。
     *
     * @param policy 淘汰策略插件，或 {@code null}
     */
    void setEvictionPolicy(EvictionPolicy<K, V> policy);

    /** 返回当前挂载的淘汰策略插件；未挂载返回 {@code null}。 */
    EvictionPolicy<K, V> evictionPolicy();
}
