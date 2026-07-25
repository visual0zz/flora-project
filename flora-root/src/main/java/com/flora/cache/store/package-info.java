/**
 * 缓存存储实现与共享引擎。
 *
 * <p>{@code CacheSupport} 是所有存储的抽象基类，仅承诺最瘦的
 * {@link com.flora.cache.Cache} 契约，负责粘合 put/get/remove、TTL、可选的
 * {@link com.flora.cache.EvictionPolicy} 回调与事件派发（含监听器异常隔离），
 * 并把「有界」「可观测」作为可 opt-in 的能力轴留给子类在类型层声明。
 * 子类只需实现一组 {@code rawXxx} 原始存储钩子。</p>
 *
 * <ul>
 *   <li>{@code BoundedCacheSupport}：本地有界缓存基类
 *       （implements {@code ObservableCache} + {@code BoundedCache}）；
 *       {@code MemoryCache} 继承它，基于 {@code ConcurrentHashMap} + TTL，
 *       并在构造时自挂 W-TinyLFU。</li>
 *   <li>{@code RemoteCacheSupport} / {@code RemoteCache}：远程（Redis 等）缓存基类，
 *       淘汰由服务端管理，本地 {@code setEvictionPolicy} 为空操作、无容量维度；
 *       子类用具体客户端实现 {@code doXxx} 钩子。</li>
 * </ul>
 */
package com.flora.cache.store;
