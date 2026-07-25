# 决策：setTtl 不应对过期键复活（修复）

- **日期**：2026-07-25
- **模块**：flora-root / com.flora.cache（CacheEngine、MemoryCache）
- **问题**：`CacheEngine.setTtl` 在 `rawContains(key)` 为 false 时有 `else` 分支仍调用 `store.rawSetTtl(key, duration)`。对本地 `MemoryCache`，过期键虽逻辑删除（lazy 过期）但物理仍在 `map` 中，`rawSetTtl` 用 `map.containsKey` 判断会误判为存在并重新赋 TTL，导致「复活」过期键。
- **决策**：`setTtl` 只对「存活键」（`rawContains == true`）生效；缺失或已过期（逻辑删除）的键一律静默忽略，绝不复活。

## 原因（Why）

- 过期键属于逻辑删除，应在 lazy `get` / `cleanUp` 中被回收；`setTtl` 重新赋 TTL 会让已过期数据「死而复生」，违反 TTL 语义与用户预期。
- 引擎层 `else` 分支本意是对缺失键兜底，但无法区分「缺失」与「已过期」，反而复活过期键。

## 应用方式（How to apply）

- `CacheEngine.setTtl`：删除 `else` 分支，仅 `if (store.rawContains(key))` 时操作并派发 TOUCH/MUTATE 事件。
- `MemoryCache.rawSetTtl`：守卫条件由 `!map.containsKey(key)` 改为 `!rawContains(key)`，作为防御——即使被直接调用也不对过期键赋 TTL。
- 该原则同样适用于其它 RawStore 实现：对过期键赋 TTL 前必须确认其存活（未被逻辑删除）。

## 影响范围

- `CacheEngine.java`：`setTtl` 移除 `else` 分支。
- `MemoryCache.java`：`rawSetTtl` 改用 `rawContains` 守卫。
- `Cache.java`：`setTtl` 文档补充「已过期（逻辑删除）静默忽略，不复活」。
- `MemoryCacheTest.java`：新增 `setTtlDoesNotReviveExpiredKey` 回归测试。
