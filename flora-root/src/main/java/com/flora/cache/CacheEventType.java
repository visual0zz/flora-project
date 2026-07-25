package com.flora.cache;

/**
 * 缓存事件类型。
 * <p>
 * 写事件：{@code INSERT}/{@code UPDATE}/{@code TOUCH}，其总和为 {@code MUTATE}；
 * 失效事件：{@code EVICT}/{@code EXPIRE}/{@code REMOVE}，其总和为 {@code INVALIDATE}。
 * 监听 {@code MUTATE} 可覆盖全部写场景，监听 {@code INVALIDATE} 可覆盖全部失效场景。
 */
public enum CacheEventType {

    // ========== 写操作（有副作用的写入） ==========

    /** 缓存项被首次写入（put 一个原本不存在的 key） */
    INSERT,

    /** 缓存项被更新覆盖（put 一个已存在的 key） */
    UPDATE,

    /** 缓存项 TTL 被刷新（{@link Cache#setTtl(Object, Duration)} 续期，不换值） */
    TOUCH,

    /**
     * 任意写操作，是 {@link #INSERT}、{@link #UPDATE}、{@link #TOUCH} 的总和。
     * <p>
     * 当任意一种写操作发生时，具体类型事件和 {@code MUTATE} 会一并触发。
     * 监听 {@code MUTATE} 即可覆盖「新建 / 更新 / 刷新 TTL」全部写场景。
     */
    MUTATE,

    // ========== 失效（被移出缓存） ==========

    /** 缓存项因淘汰策略（如 LRU/LFU）被移除 */
    EVICT,

    /** 缓存项 TTL 过期被自动清理 */
    EXPIRE,

    /** 缓存项被显式 {@link Cache#remove(Object)} 删除 */
    REMOVE,

    /**
     * 缓存项失效，是 {@link #EVICT}、{@link #EXPIRE}、{@link #REMOVE} 的总和。
     * <p>
     * 当任意一种失效发生时，具体类型事件和 {@code INVALIDATE} 会一并触发。
     */
    INVALIDATE,

    /**
     * 缓存被整体清空（{@link Cache#clear()}）。与逐条失效不同，此事件的 {@code key} 为 {@code null}，
     * 表示「全部条目失效」，监听器不应依赖具体 key（与 {@code INVALIDATE} 不属于同一聚合族）。
     */
    CLEAR,
}
