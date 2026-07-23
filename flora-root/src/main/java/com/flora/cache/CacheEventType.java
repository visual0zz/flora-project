package com.flora.cache;

/**
 * 缓存事件类型。
 * <p>
 * 用于 {@link BoundedCacheStore#addListener(CacheEventType, CacheEventListener)} 注册监听器时指定事件类型。
 */
public enum CacheEventType {

    /** 缓存项因淘汰策略（如 LRU/LFU）被移除 */
    EVICT,

    /** 缓存项 TTL 过期被自动清理 */
    EXPIRE,

    /** 缓存项被显式 {@link CacheStore#remove(Object)} 删除 */
    REMOVE,

    /**
     * 缓存项失效，是 {@link #EVICT}、{@link #EXPIRE}、{@link #REMOVE} 的总和。
     * <p>
     * 当任意一种失效发生时，具体类型事件和 {@code INVALIDATE} 会一并触发。
     */
    INVALIDATE,

    /** 缓存项被更新覆盖（put 一个已存在的 key） */
    UPDATE,

    /** 缓存项被首次写入（put 一个原本不存在的 key） */
    CREATE,
}
