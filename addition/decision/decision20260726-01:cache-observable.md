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
3. **`RemoteCache` 实现为抽象基类**：定义 `doXxx` 钩子，实现 `Cache<String,String>` +
   `ObservableCache<String,String>`，通过内部 `CacheListenerAdapter` 引擎派发事件。

## Why

- 用户明确要求「`ConcurrentHashMapCache` 只实现 `MemoryCache` 一个接口」且「把空壳
  `CacheListenerAdapter`/`RemoteCache` 实际实现」。这指向一个清晰的分层：存储与可观测职责分离。
- 装饰器模式让任意非可观测缓存（如 `RemoteCache`、或未来的 `ConcurrentHashMapCache` 实例）
  通过 `CacheListenerAdapter.of(...)` 即获得监听能力，避免在每个缓存实现里重复事件代码。

## How to apply

- 需要给 `ConcurrentHashMapCache` 加监听：`CacheListenerAdapter.of(cache)` 包一层，再 `addListener`。
  **限制**：装饰器仅观察公开 API 面，被包装缓存内部触发的淘汰/过期（`cleanUp()` 批量回收）不会派发事件。
- 新增缓存实现时，若需要可观测，优先复用 `CacheListenerAdapter` 引擎，而非各自实现监听注册。
