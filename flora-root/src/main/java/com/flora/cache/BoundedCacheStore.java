package com.flora.cache;


import java.util.Map;

/**
 * 有界缓存的能力契约，继承自 {@link CacheStore}。
 * <p>
 * 适用于本地有容量上限的缓存实现（如 LRU、LFU、FIFO），提供：
 * <ul>
 *   <li>容量查询与满检测</li>
 *   <li>手动垃圾回收（淘汰过期/多余项）</li>
 *   <li>按事件类型注册缓存事件监听器</li>
 * </ul>
 * 分布式 / Redis 等无固定容量概念的实现不应实现此接口。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface BoundedCacheStore<K, V> extends CacheStore<K, V> {

    /**
     * 执行垃圾回收，清理过期或可淘汰的缓存项。
     *
     * @return 被清理的缓存项数量
     */
    long gc();

    /**
     * 缓存是否已满。
     *
     * @return {@code true} 表示当前缓存项数量已达到容量上限
     */
    boolean isFull();

    /**
     * 缓存容量上限。
     *
     * @return 最多能容纳的缓存项数量；{@code 0} 或负数表示无上限
     */
    long capacity();

    /**
     * 添加指定类型的缓存事件监听器。
     * <p>
     * 同一事件类型可添加多个监听器，按添加顺序依次回调。
     * 重复添加同一个监听器实例不会去重。
     *
     * @param type     事件类型
     * @param listener 事件监听器；{@code null} 会被静默忽略
     */
    void addListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener);

    /**
     * 移除指定类型的某个监听器。
     * <p>
     * 使用引用相等（{@code ==}）或 {@link Object#equals} 判断。
     *
     * @param type     事件类型
     * @param listener 要移除的监听器；{@code null} 会被静默忽略
     */
    void removeListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener);

    /**
     * 移除指定类型的所有监听器。
     *
     * @param type 事件类型
     */
    void removeListeners(CacheEventType type);

    /**
     * 批量添加监听器。
     *
     * @param listeners 事件类型到监听器的映射
     */
    default void addListeners(Map<CacheEventType, CacheEventListener<? super K, ? super V>> listeners) {
        if (listeners == null) {
            return;
        }
        listeners.forEach(this::addListener);
    }

    /**
     * 清除所有事件类型的全部监听器。
     */
    default void removeAllListeners() {
        for (CacheEventType type : CacheEventType.values()) {
            removeListeners(type);
        }
    }
}
