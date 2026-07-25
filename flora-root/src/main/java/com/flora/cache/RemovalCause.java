package com.flora.cache;

/**
 * 缓存条目被移除的原因，供淘汰策略 {@link EvictionPolicy#onRemove(Object, RemovalCause)} 区分处理。
 * <p>
 * 与监听器侧的 {@link CacheEventType}（{@code REMOVE}/{@code EVICT}/{@code EXPIRE}）一一对应：
 * 引擎在显式删除、容量淘汰、TTL 过期三条移除路径上分别传入对应的 cause，使策略能针对
 * 不同移除来源做出差异化反应（例如仅对显式删除做特殊记账、对淘汰做统计）。
 */
public enum RemovalCause {

    /** 被显式 {@link Cache#remove(Object)} 删除（用户主动移除）。 */
    EXPLICIT,

    /** 触发容量上限，被淘汰策略选中移除（引擎驱动的容量回收）。 */
    EVICT,

    /** TTL 过期，被惰性删除或 {@code cleanUp()} 主动扫描清理。 */
    EXPIRE,
}
