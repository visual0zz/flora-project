# 决策：cache 事件取消惰性求值

- **日期**：2026-07-25
- **模块**：flora-root / com.flora.cache
- **决策**：移除缓存事件（`CacheEventListener.onEvent`）中 oldValue/newValue 的惰性求值（原 `Supplier` / `CompletableFuture.supplyAsync`），改为直接传递真实 `V` 值；同时在各派发点用 `if (hasListeners(type))` 包裹，避免在没有真实监听器时执行 `store.rawGet(key)` 等多余的存储读写。

## 原因（Why）

- 之前以 `Supplier`/`CompletableFuture` 形式延迟求值的设计增加了监听器侧（需调用 `.get()`/`join()`）与引擎侧的复杂度，且异步 `supplyAsync` 还会引入线程切换开销。
- 用户明确要求「取消惰性求值，直接传递真实值」，并希望通过添加 `if` 来保留「无监听器时不触发无谓存储读写」的性能收益。

## 应用方式（How to apply）

- 监听器直接读取 `oldValue`/`newValue`（真实 `V`），不要再调用任何 `.get()`/`join()`。
- 新增事件派发时，调用 `fire(...)` 前用 `if (hasListeners(type))` 守卫；凡涉及 `store.rawGet(key)` 等可能昂贵的求值，应在多个同批次事件类型下合并判断、仅求值一次（如 `UPDATE`+`MUTATE`、`TOUCH`+`MUTATE`）。
- 不要为了「避免重复求值」而重新引入 `Supplier`/回调包装：性能保护已由 `if (hasListeners(...))` 提供。

## 影响范围

- `CacheEventListener.java`：`onEvent` 签名 `Supplier` → `V`。
- `CacheEngine.java`：`fire` 接收真实值；所有 `fire` 调用点改为真实值 + `if (hasListeners)` 守卫。
- 行为语义与原惰性实现一致（UPDATE/TOUCH 的 oldValue 在 `rawPut`/`rawSetTtl` 之后取当前值）。
