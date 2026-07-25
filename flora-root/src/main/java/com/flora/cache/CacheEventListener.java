package com.flora.cache;

import java.util.concurrent.CompletableFuture;

/**
 * 缓存事件回调。
 * <p>
 * {@code oldValue} / {@code newValue} 以 {@link CompletableFuture} 形式传入：由引擎在确有监听器时
 * 通过 {@code supplyAsync} 异步求值，监听器需取值时调用 {@code Future.get()}/{@code join()} 或
 * 组合异步回调，无需取值时则不会触发任何额外的存储读写。
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
     * @param oldValue  操作前的值的异步提供者（新建类事件为 {@code null}；删除/过期/淘汰类事件为被移除前的值）
     * @param newValue  操作后的新值的异步提供者（删除/过期/淘汰类事件为 {@code null}；其余为写入/刷新后的值）
     */
    void onEvent(CacheEventType type, K key,
                 CompletableFuture<? extends V> oldValue, CompletableFuture<? extends V> newValue);
}
