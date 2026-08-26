# 决策：sanctum 存储写入改为原子替换 + 全量块缓存

- 日期：2026-08-26
- 模块：`flora-sanctum-core`（`store` / `model.impl`）
- 关联审查：`addition/codereview/review20260826-01-sanctum-storage.md`（问题 2、问题 6）

## 决策

1. **写入采用临时文件 + 原子替换**：`MarkdownObjectStore.put` 先写同目录 `.tmp` 临时文件，再 `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` 替换目标；`ATOMIC_MOVE` 不支持时降级为普通 `REPLACE_EXISTING`；任何异常路径清理残留 `.tmp`（`scan` 仅匹配 `.md`，残留 `.tmp` 不会被误读）。
2. **`ObjectStore.put` 返回 `Block`**：调用方写入后可直接拿到块元数据（文件位置、时间戳、字节），用于回写内存缓存。
3. **`TreeContext` 全量缓存块**：`scanAll` 缓存全部扫描到的块（含当前不可解密的）到 `blocks`，仅解密成功的进对象图；`writeCipherBlock` 在 `put` 后把返回的 `Block` 也放入 `blocks`。`blockOf` 从此对新写入/已存在块直接命中，不再触发二次全扫。
4. **`nextTimestamp` 维持读磁盘不变**：每次 `store.scan()` 取全库最大时间戳，以感知外部/同步拉入的新块时间戳，保留跨进程冲突仲裁（大者赢）的安全性。

## Why

- 审查发现存储层两处问题：
  - 问题 2：`blockOf` 缓存 miss 时二次全量扫描（`store.scan()`）。根因是 `write` 不回写 `blocks` 缓存。
  - 问题 6：直接覆盖写文件，崩溃/掉电可能留下半写块（不可解密即损坏），原设计靠 git 兜底，未启用 git 时暴露。
- 原子替换直接消除半写风险（高收益）；全量块缓存消除 `blockOf` 冗余扫描（代码整洁收益）。

## How to apply

- 此后所有写路径（运行时经 `TreeContext.write`，建库/写 manifest 经 `VaultCreator`/`ManifestStore`）都通过 `ObjectStore.put` 完成，自动获得原子性与返回的 `Block`。
- 若未来引入多写者（同步线程或并发进程），需在 `ObjectStore` 层加写锁，单进程串行假设不再成立。
- `nextTimestamp` 的磁盘扫描不要改为纯内存缓存，否则丢失跨进程时间戳安全。
