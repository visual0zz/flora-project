package com.flora.cache;

/**
 * 缓存事件类型。
 * <p>
 * 每个常量对应缓存的一个<b>具体对外操作</b>（或一条内部生命周期事件），彼此独立、互不聚合；
 * 不再提供 {@code MUTATE} / {@code INVALIDATE} 之类的复合聚合类型——若想监听多种操作，需为每个类型分别注册。
 * <p>
 * 操作类事件（由 {@code CacheListenerAdapter} 在拦截到对应公开 API 调用时派发）：
 * <ul>
 *   <li>{@code PUT}：写入（{@link Cache#put(Object, Object)} 及其带 TTL 重载），覆盖新建与覆盖写。</li>
 *   <li>{@code PUT_IF_ABSENT}：原子写入（{@link Cache#putIfAbsent(Object, Object)} 及其带 TTL 重载），
 *       仅当真正写入（key 原先不存在）时派发。</li>
 *   <li>{@code GET}：读取（{@link Cache#get(Object)}）；{@code newValue} 为读到的值，未命中为 {@code null}。</li>
 *   <li>{@code GET_TTL}：查询剩余过期（{@link Cache#ttl(Object)}），仅携带 {@code key} 信号
 *       （返回的 {@code Duration} 非 {@code V}，不放入 {@code newValue}）。</li>
 *   <li>{@code CONTAINS}：查询存在（{@link Cache#containsKey(Object)}），仅携带 {@code key} 信号。</li>
 *   <li>{@code SET_TTL}：刷新过期（{@link Cache#setTtl(Object, java.time.Duration)}）。</li>
 *   <li>{@code REMOVE}：显式删除（{@link Cache#remove(Object)}）。</li>
 *   <li>{@code CLEAR}：整体清空（{@link Cache#clear()}），{@code key} 为 {@code null}。</li>
 * </ul>
 * 内部生命周期事件（由缓存引擎 / 后端驱动，<b>不经过装饰器</b>，故 {@code CacheListenerAdapter} 不派发）：
 * <ul>
 *   <li>{@code EVICT}：被淘汰策略移除。</li>
 *   <li>{@code EXPIRE}：TTL 过期被自动清理。</li>
 * </ul>
 */
public enum CacheEventType {

    // ========== 写入 ==========

    /** 写入（put / put(key, value, Duration)），覆盖新建与覆盖写。 */
    PUT,

    /** 原子写入（putIfAbsent / 带 TTL 重载），仅当真正写入（key 原先不存在）时派发。 */
    PUT_IF_ABSENT,

    // ========== 读取 ==========

    /** 读取（get）；newValue 为读到的值，未命中为 null。 */
    GET,

    /** 查询存在（containsKey）；仅携带 key 信号。 */
    CONTAINS,

    // ========== TTL 维护 ==========

    /** 查询剩余过期（ttl）；仅携带 key 信号（返回的 Duration 非 V，不放入 newValue）。 */
    GET_TTL,

    /** 刷新过期（setTtl）。 */
    SET_TTL,

    // ========== 移除 ==========

    /** 显式删除（remove）。 */
    REMOVE,

    /** 整体清空（clear）；key 为 null，监听器不应依赖具体 key。 */
    CLEAR,

    // ========== 内部生命周期（装饰器不派发） ==========

    /** 被淘汰策略移除（内部事件，不由 CacheListenerAdapter 派发）。 */
    EVICT,

    /** TTL 过期被自动清理（内部事件，不由 CacheListenerAdapter 派发）。 */
    EXPIRE,
}
