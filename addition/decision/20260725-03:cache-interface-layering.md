# 决策：Cache 接口三层分层（CacheStore / ObservableCacheStore / BoundedCacheStore）

- 日期：2026-07-25
- 模块：flora-root / com.flora.cache

## 背景

`20260725-02` 采用「`CacheStore` 单一胖接口 + EvictionPolicy 插件」的写法，但 `CacheStore`
方法堆叠过多（存储 + 容量/gc + 事件 + 策略挂载），且存在两种结构隐患：
1. `CacheStore` 过胖，违背接口隔离；
2. 之前 `ComposedCacheStore(实现 BoundedCacheStore) 持有 CacheStore backing` 导致的
   is-a+has-a 循环与「缓存套缓存」嵌套。

经多轮讨论，确定采用**三层线性接口**，把三种正交能力按层拆分。

## 决策：接口分层

```
CacheStore<K,V>                  // L1 存储（最瘦）：put*/get/setTtl/ttl/remove/clear/approxCount/containsKey + keys()/isExpired()
  ▲ extends
ObservableCacheStore<K,V>        // L2 行为·可观测：事件监听(addListener…)
  ▲ extends
BoundedCacheStore<K,V>           // L3 尺寸·有界：capacity/isFull/gc + setEvictionPolicy/evictionPolicy
  ▲ implements
AbstractCacheStore<K,V>          // 基类：持有可选 EvictionPolicy + 监听器 + 容量，驱动淘汰/fire；子类实现 rawXxx
  ▲ extends
ConcurrentHashMapStore / RemoteCache / MemoryCache
```

- **L1 管数据**：纯存储，最小、最易被各类后端实现。
- **L2 管行为(观测)**：叠加事件监听；任何想被监听的缓存至少到这一层。
- **L3 管尺寸+策略**：叠加容量（何时淘汰）与淘汰策略插件（淘汰谁）。二者正交，合起来才真正淘汰。

## 关键决策点：策略挂载放哪层

讨论过 L1 / L2 / L3 三种放法，权衡后**用户拍板放 L3（`BoundedCacheStore`）**：
- L1：最灵活但把最瘦的存储接口又撑胖，且纯存储被迫知道淘汰概念。
- L2：策略(算法)与容量(尺寸)彻底分层，符合「why only bounded can mount policy」的质疑；
  但 L2 引用能挂策略却无容量，需靠 `capacity>0` 闸门定义「休眠」状态。
- L3（采用）：「有界=有尺寸+会淘汰」符合直觉，挂载即有意义、无休眠困惑；代价是策略与尺寸
  焊在同一层（算法不再能被无界缓存单独装备）。

配套约束：`AbstractCacheStore` 对策略回调(`onPut/onAccess/onRemove`)加 `capacity > 0` 闸门——
即使 L3 下 `BoundedCacheStore` 也可 `capacity<=0`（无界），策略仍休眠、不空转记账；设了容量才参与淘汰。

## 结构安全性

- 无 is-a+has-a 循环：`AbstractCacheStore` 内部持有原始数据结构(CHM/expiry map) + `EvictionPolicy`
  插件，**不是** `CacheStore` 字段。
- 无嵌套：插件挂载点只收 `EvictionPolicy`，不是另一个 `CacheStore`；编译期杜绝「缓存套缓存」。

## 影响

- 新增：`ObservableCacheStore`。
- 改写：`CacheStore`（瘦身为存储）、`BoundedCacheStore`（extends ObservableCacheStore，加策略挂载）、
  `AbstractCacheStore`（`implements BoundedCacheStore` + 容量闸门）、`MemoryCache`/`RemoteCache` 文档。
- 删除：`ComposedCacheStore`（粘合逻辑已入 `AbstractCacheStore`）。
- 测试：无需改动（`CacheStore` 仍是顶层超类型；事件/策略调用都在 `MemoryCache`/`ConcurrentHashMapStore` 上）。

> 取代 `20260725-02` 中「单一胖 CacheStore」的取向；W-TinyLFU 去重、事件语义(INSERT/UPDATE/TOUCH/MUTATE)、
> 监听器异常隔离仍然有效。
