# 决策：统一 cache TTL 方法的 Duration 语义

- **日期**：2026-07-25
- **模块**：flora-root / com.flora.cache（CacheEngine、RawStore、MemoryCache、RemoteCache）
- **决策**：统一 TTL 相关方法的 `Duration` 语义约定：
  - `Duration.MAX` = 不设置过期时间 / 过期时间无限（永不过期）。
  - `Duration.ZERO` = 已过期或不存在。
  - 其余为正数剩余时长。

## 原因（Why）

- 原 `rawTtl` 契约混乱：永不过期返回 `ZERO`、不存在返回 `null`，与「ZERO 表示已过期」的直觉相反，且 `null` 返回值需要调用方额外判空。
- 用户要求统一约定，使 TTL 方法的返回值与入参（setTtl/put 的 duration）语义一致、可读、可组合。

## 应用方式（How to apply）

- `ttl(key)` / `rawTtl(key)`：不存在或已过期返回 `ZERO`；永不过期返回 `MAX`；否则返回剩余 `Duration`。
- `setTtl(key, MAX)` / `put(key, value, MAX)` / `putIfAbsent(key, value, MAX)`：表示永不过期（存储层转译为「无过期」/PERSIST），不要抛异常也不要当正数处理。
- `setTtl(key, ZERO)` / `put(key, value, ZERO)`：表示立即过期（引擎已对零/负时长改走过期删除管线）；存储层 `rawXxx` 收到 `ZERO` 也应视为立即过期。
- 存储实现必须把 `Duration.MAX` 当作永不过期处理，禁止对其调用 `Duration.toMillis()`（会溢出抛异常）。MemoryCache 用「expiry 映射不存在」表示永不过期；RemoteCache 用 `NO_EXPIRE(-1)` 转译 `MAX`。

## 影响范围

- `RawStore.java`：更新 `rawTtl` 契约与类注释。
- `Cache.java`：`ttl(K)` 注释（永不过期→MAX，不存在/已过期→ZERO）。
- `MemoryCache.java`：`rawTtl` 返回 MAX/ZERO；`computeExpiry`/`rawSetTtl` 识别 MAX→永不过期；移除无用 `IMMORTAL` 常量并简化 `expired`。
- `RemoteCache.java`：`rawTtl` 按 Redis PTTL（−2/−1）映射为 ZERO/MAX；新增 `toTtlMillis` 处理 MAX→NO_EXPIRE；`doExpire` 文档新增 `NO_EXPIRE` 表示 PERSIST。
- `CacheEngine.java`：`setTtl` 注释说明 MAX 语义（逻辑不变，存储层处理）。
