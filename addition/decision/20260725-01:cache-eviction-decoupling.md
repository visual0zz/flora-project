# 决策：缓存存储与淘汰策略解耦（组合式架构）

- **日期**：2026-07-25
- **模块**：flora-root / com.flora.cache
- **决策者**：CodeBuddy（AI 代理）

## 背景

原有 `MemoryCache` 把存储（`ConcurrentHashMap` + TTL）、W-TinyLFU 三段 LRU
索引与 Count-Min Sketch 频率估计焊死在一个 ~700 行的类里。用户要求：
1. 存储与淘汰策略可**自由组合**；
2. 同时也兼容"存储+策略焊死的整体"单体实现。

## 决策

采用**三层组合式架构**，满足自由组合 + 单体兼容：

- `CacheStore<K,V>`（已有）：纯 KV + TTL 存储契约，新增默认方法
  `keys()`（供 `gc()` 扫描过期，O(n) 仅低频场景）与 `isExpired(K)`
  （默认 `false`）。`remove(K)` 改为返回被删 value（组合层需要它触发事件）。
- `EvictionPolicy<K,V>`（新增）：存储无关的淘汰策略接口。
  `onPut/onAccess/onRemove/evict()/clear()`。策略自管全部索引与统计，
  `evict()` 以 O(1) 返回待淘汰 key；被淘汰 key 由组合层负责删除并触发事件，
  策略不碰 value、不触发监听器。
- `ComposedCacheStore<K,V>`（新增）：把任意 `CacheStore` + 任意
  `EvictionPolicy` 粘合成 `BoundedCacheStore`。在写/读/删时通知策略，
  并在容量超限时驱动 `evict()` 循环删除 + 触发 `EVICT`/`INVALIDATE`。
- 单体兼容：任何把存储+策略焊死、对外实现 `BoundedCacheStore` 的类天然兼容
  （调用方只认 `BoundedCacheStore`）。`MemoryCache` 重构为继承
  `ComposedCacheStore`，内部用 `ConcurrentHashMapStore` + `WTinyLfuEvictionPolicy`
  组装——不再重复 W-TinyLFU 代码。

提供四种可插拔策略（均 `key` 基于、与存储解耦）：
`WTinyLfuEvictionPolicy`（原算法逐字移植）、`LRUEvictionPolicy`、
`FIFOEvictionPolicy`、`LFUEvictionPolicy`。

## 关键设计取舍

- **淘汰走策略内部索引（O(1)），不遍历 `keys()`**：避免用户原始方案里
  `selectEvictKey(store)` + 遍历 `keys()` 的 O(n) 淘汰陷阱。`keys()` 仅用于 `gc()`。
- **`evict()` 内部自循环排空窗口**：原 `MemoryCache.ensureCapacity()` 的
  `while` 在"准入分支（无存储删除）"时仍继续清窗口；若 `enforce` 用
  `while (evict()!=null)` 会在准入分支提前终止，导致窗口堆积、容量被突破。
  因此 `evict()` 内部循环直到窗口降到 `windowMax` 或容量足够，仅在真正要删
  存储项时返回 key。重构后行为与原 `MemoryCache` 逐字一致（同容量同脚本下
  幸存集合与 size 完全相同）。
- **写入顺序保真**：新 key 采用"先 `enforce()` 淘汰、后 `onPut` 加入"，
  与原始 `ensureCapacity()` 先于 `addNew()` 的时序一致。
- **LRU/FIFO/LFU 用 `>= capacity` 门槛**（严格 ≤ 容量）；W-TinyLFU 保留
  原始 `> capacity - windowMax` / `> capacity` 门槛以维持其既有（带窗口松弛的）行为。

## 影响

- `CacheStore.remove` 返回类型由 `void` 改为 `V`（影响实现类 `RemoteCache`、
  `ConcurrentHashMapStore` 同步更新；调用方忽略返回值仍兼容）。
- 新增 4 个策略类 + `ComposedCacheStore` + `MemoryCache` 重写为组合式。
- 行为保真已通过对比审计（同脚本幸存集合一致）及新增单测（1173 测试全绿）验证。
