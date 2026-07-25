package com.flora.cache;

import java.util.function.Supplier;

/**
 * 缓存事件回调。
 * <p>
 * {@code oldValue} / {@code newValue} 以 {@link Supplier} 形式传入：仅在确有监听器关注时
 * 才被求值，避免为无人读取的值触发额外的存储读写。
 * 监听器中调用 {@code oldValue.get()} / {@code newValue.get()} 取得实际值。
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
     * @param oldValue  操作前的值的惰性提供者（新建类事件为 {@code null}；删除/过期/淘汰类事件为被移除前的值）
     * @param newValue  操作后的新值的惰性提供者（删除/过期/淘汰类事件为 {@code null}；其余为写入/刷新后的值）
     */
    void onEvent(CacheEventType type, K key, Supplier<? extends V> oldValue, Supplier<? extends V> newValue);
}
