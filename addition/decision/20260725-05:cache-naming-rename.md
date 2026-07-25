---
name: 缓存类型命名规范化（Cache 词根 + Support 后缀）
subject: 将 CacheStore/ObservableCacheStore/.../AbstractXxx 统一重命名为 Cache 词根 + Support 后缀
date: 2026-07-25
module: flora-root (com.flora.cache)
---

## 决策背景

`20260725-04` 落地的接口与抽象基类命名存在两处不顺手之处：
1. `EvictionConfigableCacheStore` 的 `Configable` 非标准拼写（应为 `Configurable`），且整体读起来拗口；
2. 接口统一以 `CacheStore` 结尾、抽象基类统一 `Abstract` 前缀，略冗余。

用户拍板采用一套自洽命名：**接口统一 `XxxCache` 词根、抽象基类统一 `Support` 后缀**（去掉 `Store` 与 `Abstract`）。

## 重命名映射

| 原名称 | 新名称 |
|---|---|
| `CacheStore` (接口) | `Cache` |
| `ObservableCacheStore` (接口) | `ObservableCache` |
| `EvictionConfigableCacheStore` (接口) | `EvictableCache` |
| `BoundedCacheStore` (接口) | `BoundedCache` |
| `AbstractCacheStore` (共享引擎) | `CacheSupport` |
| `AbstractBoundedCacheStore` (有界基类) | `BoundedCacheSupport` |
| `AbstractRemoteCache` (远程基类) | `RemoteCacheSupport` |
| `MemoryCache` / `RemoteCache` (具体类) | 不变 |

## 类型关系（不变）

- 接口轴：`Cache` ← {`ObservableCache`, `EvictableCache`}；`BoundedCache extends EvictableCache`。
- 类：`CacheSupport implements Cache`（最小承诺）；`BoundedCacheSupport extends CacheSupport implements ObservableCache, BoundedCache`；`RemoteCacheSupport extends CacheSupport implements ObservableCache, EvictableCache`（不实现 `BoundedCache`）；`MemoryCache extends BoundedCacheSupport`；`RemoteCache extends RemoteCacheSupport`。

## 取舍

- 优点：接口名更短、`XxxCache` 词根一致；`Support` 比 `Abstract` 更表意"供子类复用骨架"；`Evictable` 准确表达"可被淘汰/可挂策略"的能力语义。
- 代价：跨文件重命名（仅 flora-root 内，已 `git mv` 保留历史）；包名 `com.flora.cache` 与类型 `Cache` 并存，但无歧义（`Cache` 指根契约类型）。

## 验证

`flora-root` 全量 **1180** 测试通过。引用范围扫描确认仅 flora-root（main+test）使用这些类型，其他模块（含 `ConverterRegistry`，其仅依赖 `MemoryCache`）未受影响。

## 涉及文件

- 重命名：`CacheStore→Cache`、`ObservableCacheStore→ObservableCache`、`EvictionConfigableCacheStore→EvictableCache`、`BoundedCacheStore→BoundedCache`、`AbstractCacheStore→CacheSupport`、`AbstractBoundedCacheStore→BoundedCacheSupport`、`AbstractRemoteCache→RemoteCacheSupport`
- 同步改动引用：`MemoryCache`、`RemoteCache`、`CacheEventType`、`EvictionPolicy`、`EvictionPolicyTest`
- 决策记录：同步更新 `20260725-04`
