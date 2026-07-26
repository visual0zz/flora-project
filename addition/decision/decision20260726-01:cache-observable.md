# 决策：缓存可观测能力下沉到 CacheListenerAdapter（装饰器）

- 日期：2026-07-26
- 模块：flora-root / com.flora.cache

## 背景

`ConcurrentHashMapCache` 原本直接 `implements Cache, ObservableCache, MemoryCache, BoundedCache`，
把「存储 + 淘汰 + TTL」与「事件派发」耦合在一个类里，并且 `CacheListenerAdapter` 与 `RemoteCache`
当时都是空壳（未实现）。

## 决策

1. **`ConcurrentHashMapCache` 只实现 `MemoryCache<K,V>`**（其传递实现了 `BoundedCache`、`Cache`）。
   移除类内全部监听器字段与 `addListener`/`fire`/`hasListeners` 等可追溯逻辑；它退化为纯内存存储缓存。
2. **`CacheListenerAdapter` 作为可观测装饰器**：包装 `Cache`/`BoundedCache`/`MemoryCache`，
   委托全部读写操作，在拦截到的显式操作（`put`/`putIfAbsent`/`remove`/`clear`/`setTtl`）上派发事件；
   通过 `of(...)` 工厂按被包装类型返回最具体的可观测视图（`ObservableMemoryCache` 等）。
   同时其构造器对同包可见，可被 `RemoteCache` 当作「事件引擎」复用。
3. **`RemoteCache` 实现为纯 `Cache<String,String>` 抽象基类**：仅定义 `doXxx` 钩子，
   **不实现 `ObservableCache`、不持有任何监听器、不内嵌事件引擎**。如需事件监听，同样用
   `CacheListenerAdapter.of(remoteCache)` 装饰包装即可，与 `ConcurrentHashMapCache` 一致。
   这样所有缓存实现都无需重复事件代码，监听职责唯一落在装饰器上。

## Why

- 用户明确要求「`ConcurrentHashMapCache` 只实现 `MemoryCache` 一个接口」且「把空壳
  `CacheListenerAdapter`/`RemoteCache` 实际实现」。这指向一个清晰的分层：存储与可观测职责分离。
- 装饰器模式让任意非可观测缓存（如 `RemoteCache`、或未来的 `ConcurrentHashMapCache` 实例）
  通过 `CacheListenerAdapter.of(...)` 即获得监听能力，避免在每个缓存实现里重复事件代码。

## How to apply

- 需要给 `ConcurrentHashMapCache` 或 `RemoteCache` 加监听：`CacheListenerAdapter.of(cache)` 包一层，
  再 `addListener`。`RemoteCache` 内部不持有监听器，只能通过这种方式获得可观测能力。
  **限制**：装饰器仅观察公开 API 面，被包装缓存内部触发的淘汰/过期（`cleanUp()` 批量回收、
  远端后端的 maxmemory 淘汰/过期）不会派发事件。
- 新增缓存实现时，若需要可观测，用 `CacheListenerAdapter.of(...)` 装饰包装即可，
  而非让缓存自身实现监听注册或内嵌事件引擎。
