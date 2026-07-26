package com.flora.cache.eviction;

/**
 * 触发淘汰策略「移除」回调的原因。
 * <p>
 * 覆盖 {@link com.flora.cache.CacheEventType} 的 INVALIDATE 聚合族，用于以单一
 * {@link EvictionPolicy#onRemove(Object, RemoveReason)} 取代原先的
 * {@code onRemove / onEvict / onExpire / onInvalidate} 拆分：
 * <ul>
 *   <li>{@link #EVICT}：因容量超限、由策略自身选出的受害者被淘汰；</li>
 *   <li>{@link #EXPIRE}：TTL 过期被自动清理；</li>
 *   <li>{@link #REMOVE}：被显式 {@code remove} 删除。</li>
 * </ul>
 * 三种原因对策略内部状态的影响通常一致（都是从追踪结构中摘除 key）；如需区分（如统计）
 * 可按枚举分别处理。整体清空（{@code CLEAR}）不属于逐条失效，仍由
 * {@link EvictionPolicy#onClear()} 单独通知。
 *
 * @see EvictionPolicy#onRemove(Object, RemoveReason)
 */
public enum RemoveReason {

    /** 因容量超限、由策略选出的受害者被淘汰（对应 CacheEventType#EVICT） */
    EVICT,

    /** TTL 过期被自动清理（对应 CacheEventType#EXPIRE） */
    EXPIRE,

    /** 被显式 remove 删除（对应 CacheEventType#REMOVE） */
    REMOVE,
}
