# 决策：删除 CacheEngine，逻辑内联进 MemoryCache / RemoteCache

- **日期**：2026-07-25
- **模块**：flora-root / com.flora.cache.store
- **决策**：删除 `CacheEngine` 与 `RawStore`（`RawStore` 是引擎与存储之间的 SPI，引擎删除后无存在必要）。把原本在引擎里的缓存编排 + 事件派发逻辑**分别写进** `MemoryCache` 与 `RemoteCache` 两个类：
  - `MemoryCache`：完整内联（写入/读取/TTL/惰性+主动过期/淘汰策略/容量/`cleanUp`/事件派发）。
  - `RemoteCache`：**精简**内联（写入/读取/TTL/删除/事件派发），**不内联**淘汰、容量与本地过期扫描——远端过期/淘汰由后端负责，故其不实现 `EvictableCache`/`BoundedCache`。
  - 事件派发逻辑（addListener/removeListener/removeListeners/hasListeners/fire + 异常隔离）**完整复制**到两个类，不新建任何共享辅助类（按用户要求「完全内联不新增类」）。

## 原因（Why）

- `RemoteCache` 侧实际用不到引擎里大半的过期/淘汰分支（`rawIsExpired` 恒 false、`rawKeys` 恒空、`capacity<=0`），单引擎「一刀切」让远程侧拖着一堆死分支，结构不清。
- 用户明确要求删引擎、逻辑分别内联、RemoteCache 专门精简，且事件逻辑也内联（不引入共享类）。

## 应用方式（How to apply）

- 新增缓存能力如需共享，优先在两个类里各写一份（保持无共享引擎），而非恢复 `CacheEngine` 式基类。
- `RemoteCache` 永远是精简版：不得加入本地过期扫描、淘汰或容量逻辑。
- 公开契约（`MemoryCache`/`RemoteCache` 的构造器与 `Cache`/`ObservableCache` 等方法）保持不变，现有测试（1182 个）全部通过。

## 影响范围

- 删除：`store/CacheEngine.java`、`store/RawStore.java`。
- 重写：`store/MemoryCache.java`（内联完整逻辑，rawXxx 改私有 storeXxx）、`store/RemoteCache.java`（精简内联，doXxx 钩子与 TTL 语义映射保留）。
- 注释：`EvictionPolicy.java` 去除对 `CacheEngine` 的引用。

> 说明：本决策有意不修订既有的 `20260725-04/05`（`CacheSupport` 共享基类方案，从未落地）；按用户「不要在意旧决策文件」的口径，以本记录作为本次重构的实际锚点。
