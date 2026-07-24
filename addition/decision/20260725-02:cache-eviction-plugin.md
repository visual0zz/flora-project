# 决策：淘汰策略作为 CacheStore 的插件（非平等组合）

- 日期：2026-07-25
- 模块：flora-root / com.flora.cache

## 背景

`20260725-01` 采用「`CacheStore` + `EvictionPolicy` 平等组合成新类型 `ComposedCacheStore`（实现 `BoundedCacheStore`）」的写法。
复盘发现该写法存在认知与结构问题：

1. `ComposedCacheStore` 由 `CacheStore` + `EvictionPolicy` 粘合，却又 `is-a CacheStore`（经由
   `BoundedCacheStore extends CacheStore`），形成「既是存储、又包含存储」的循环。
2. 因为 `MemoryCache extends ComposedCacheStore` 仍是 `CacheStore`，`new ComposedCacheStore<>(某MemoryCache, policy, cap)`
   能编译，导致「缓存套缓存 / 反复叠加 EvictionPolicy」的嵌套——双层独立记账、双重淘汰、容量互相打架、事件翻倍、命中率下降。

## 决策

改为**插件模型**：

- `CacheStore` 是唯一对外缓存契约：存储 + 容量/gc + 事件 + 可选 `EvictionPolicy` 插件
  （`setEvictionPolicy` / `evictionPolicy`）。无论是否挂载策略，一个 `CacheStore` 都直接可用。
- `EvictionPolicy` 是**挂在缓存上的可选插件**，不是与存储平等组合出的新类型。挂载点收的是策略对象，
  不是另一个 `CacheStore`，因此**不可能嵌套**；重复挂载只会替换插件。
- `AbstractCacheStore` 抽象基类托管可选策略、事件派发与淘汰驱动；具体存储（`ConcurrentHashMapStore`、
  `RemoteCache`）只实现原始 KV+TTL 的 `rawXxx` 钩子。单体焊死实现直接 `implements CacheStore` 即可。
- `BoundedCacheStore` 接口删除（有界能力已被「CacheStore + 策略插件」吸收）；`ComposedCacheStore` 删除
  （粘合逻辑移入 `AbstractCacheStore`）。`MemoryCache` 改为 `ConcurrentHashMapStore` 子类，构造时挂上
  `WTinyLfuEvictionPolicy`。

## 关键取舍

- 优点：单一接口、无类型层级爆炸、无 is-a+has-a 循环、编译期杜绝嵌套、调用方始终只认 `CacheStore`。
- 代价：`CacheStore` 接口比纯存储更「重」（含容量/事件/插件挂载点），每个实现都要容忍插件缺省；
  `RemoteCache` 的本地淘汰无意义（`setEvictionPolicy` 为空操作，淘汰由服务端管理）。

## 影响

- 新增：`AbstractCacheStore`。
- 改写：`CacheStore`（扩为完整缓存契约）、`ConcurrentHashMapStore`（继承基类）、`RemoteCache`（继承基类）、
  `MemoryCache`（子类 + 插件）。
- 删除：`ComposedCacheStore`、`BoundedCacheStore`。
- 测试：`EvictionPolicyTest` / `MemoryCacheTest.composableSmoke` 改为通过 `setEvictionPolicy` 装配；
  `CacheEventTypesTest` 继续覆盖写事件语义。

> 本决策取代 `20260725-01` 中「组合式 new-type」的取向；01 记录的 W-TinyLFU 去重与事件语义（INSERT/UPDATE/TOUCH/MUTATE、
> 监听器异常隔离）仍然有效。
