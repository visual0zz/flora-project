/**
 * 缓存抽象层：以「正交能力轴 + 淘汰策略插件」为核心的缓存契约与事件模型。
 *
 * <h2>分层与正交能力轴</h2>
 * 最瘦的契约是 {@link Cache}：只谈 KV 与 TTL，便于各种后端实现。在其上分出两条
 * <b>正交</b>的能力轴（均直接 {@code extends Cache}）：
 * <ul>
 *   <li><b>可观测轴</b>——{@link ObservableCache}：叠加事件监听
 *       （{@code INSERT}/{@code UPDATE}/{@code TOUCH}/{@code MUTATE} 写事件，
 *        {@code EVICT}/{@code EXPIRE}/{@code REMOVE}/{@code INVALIDATE} 失效事件）。</li>
 *   <li><b>淘汰轴</b>——{@link EvictableCache}：叠加「挂载 / 卸除 {@link EvictionPolicy} 插件」的能力；
 *       {@link BoundedCache} 再在其上叠加容量约束（{@code capacity}/{@code isFull}/{@code gc}）。</li>
 * </ul>
 * 两条轴相互独立：一个缓存可以「有界但不可观测」，也可以「可观测但无界」，
 * 或两者兼具（如 {@code MemoryCache}），或两者皆无（纯 {@link Cache}）。
 * 「能挂策略」与「有硬容量」是两件正交的事——其一 alone 已是合法类型
 * （无界但挂策略 = 仅统计 / 准入；有界但未挂策略 = 不淘汰，容量只是上限标记）；
 * 真正发生淘汰需要二者同时就位。
 *
 * <h2>淘汰策略插件模型</h2>
 * {@link EvictionPolicy} 只做淘汰决策、不碰存储与事件：通过 {@code onPut}/{@code onAccess}/{@code onRemove}
 * 接收 key 的读写通知（自行维护 LRU 分段 / 频率素描等索引），并通过 {@code evict()} 拉取一个待淘汰 key。
 * 被淘汰的 key 由挂载它的缓存引擎负责从存储删除并触发 {@code EVICT}/{@code INVALIDATE} 事件，
 * 故策略与具体存储实现完全解耦，可自由替换（LRU / LFU / FIFO / W-TinyLFU）。
 * <p>
 * 策略回调的唤醒闸门是「策略已挂载（{@code policy != null}）」：无界但挂了策略的缓存同样向策略喂数据，
 * 只是 {@code evict()} 在容量未超限时返回 {@code null}，从而只统计不删除——使「可挂策略」与「有界」正交。
 *
 * <h2>实现与引擎</h2>
 * 具体存储实现位于 {@code com.flora.cache.store} 子包，共享抽象基类
 * {@code CacheSupport}（粘合 put/get/remove、TTL、策略回调与事件派发）。
 * 本地有界缓存继承 {@code BoundedCacheSupport}（如 {@code MemoryCache}，构造时自挂 W-TinyLFU）；
 * 远程缓存继承 {@code RemoteCacheSupport}（淘汰交由服务端，本地 {@code setEvictionPolicy} 为空操作）。
 *
 * @see Cache
 * @see ObservableCache
 * @see EvictableCache
 * @see BoundedCache
 * @see EvictionPolicy
 */
package com.flora.cache;
