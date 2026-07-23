package com.flora.cache;


@FunctionalInterface
public interface CacheEventListener<K, V> {

    /**
     * 缓存事件回调。
     *
     * @param type  事件类型
     * @param key   被操作的键
     * @param value 被操作的值（如果是删除/过期事件，为被移除前的值）
     */
    void onEvent(CacheEventType type, K key, V value);
}
