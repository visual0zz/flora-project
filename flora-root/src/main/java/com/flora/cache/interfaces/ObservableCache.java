package com.flora.cache.interfaces;

import com.flora.cache.CacheEventType;

import java.util.Map;

/**
 * 可观测缓存契约：提供缓存事件监听能力。
 * <p>
 * 支持对 {@code INSERT}/{@code UPDATE}/{@code TOUCH}/{@code MUTATE}/{@code EVICT}/
 * {@code EXPIRE}/{@code REMOVE}/{@code INVALIDATE} 各类事件注册监听器。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface ObservableCache<K, V> extends Cache<K, V> {

    /**
     * 注册指定类型的缓存事件监听器。
     * <p>同一事件类型可添加多个监听器，按添加顺序依次回调；重复添加同一实例不去重。
     * 监听器内部抛异常不会影响缓存主流程，也不跳过同批次其他监听器。
     *
     * @param type     事件类型
     * @param listener 监听器；{@code null} 静默忽略
     */
    void addListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener);

    /** 移除指定类型的某个监听器（引用相等或 {@link Object#equals} 判断）。 */
    void removeListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener);

    /** 移除指定类型的所有监听器。 */
    void removeListeners(CacheEventType type);

    /** 批量添加监听器。 */
    default void addListeners(Map<CacheEventType, CacheEventListener<? super K, ? super V>> listeners) {
        if (listeners == null) return;
        listeners.forEach(this::addListener);
    }

    /** 清除所有事件类型的全部监听器。 */
    default void removeAllListeners() {
        for (CacheEventType type : CacheEventType.values()) {
            removeListeners(type);
        }
    }
}
