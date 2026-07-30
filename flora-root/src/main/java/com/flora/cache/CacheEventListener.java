package com.flora.cache;

/**
 * 缓存事件回调。
 * <p>
 * {@code oldValue} / {@code newValue} 以真实值直接传入。
 * 为避免为无人关注的事件触发多余的存储读写，引擎在派发前通过 {@code if (hasListeners(type))}
 * 判断：仅在确有监听器时才求值并派发，监听器中直接读取 {@code oldValue} / {@code newValue} 即可。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
@FunctionalInterface
public interface CacheEventListener<K, V> {

    /**
     * 缓存事件回调。
     *
     * @param type      事件类型
     * @param key       被操作的键
     * @param oldValue  操作前的值（新建类事件为 {@code null}；删除/过期/淘汰类事件为被移除前的值）
     * @param newValue  操作后的值（删除/过期/淘汰类事件为 {@code null}；其余为写入/刷新后的值）
     */
    void onEvent(CacheEventType type, K key, V oldValue, V newValue);
}
