# decision20260902-01-sanctum

**决策**：组密钥采用「双 DEK（dek1 退役中 / dek2 活跃）+ 惰性前向保密轮换」方案；软删除块重加密到 dek2。

**模块**：flora-sanctum-core（密钥层级 / 存储格式 / 树写路径）

**日期**：2026-09-02

## 背景

原设计每个持有 DEK 的组节点只存一个 DEK，子节点统一用它加密。一旦需要「密钥轮换」
（如换主密码后或前向保密需求），旧 DEK 下仍挂着大量现存子块，无法干净丢弃旧键。

用户提出：每个组持有两个 DEK，新建/编辑子节点一律用 dek2；每次修改组后判定 dek1 是否还被
使用，若已无任何子节点使用则丢弃 dek1、把 dek2 提升为 dek1 并随机新建一个 dek2。这是一种
**惰性前向保密（lazy forward-secret）轮换**。

## 决策

### 存储格式（不单独包裹 DEK）

- 组 JSON 内直接存 `dek1` / `dek2`（明文 base64），与 JSON 其余字段一起整体被**父组 DEK**
  加密；不单独给 DEK 再裹一层（双层加密无用且占空间）；
- 旧格式单 `dek` 字段向后兼容：`dek1 == dek2`，解锁时按单 DEK 读入、无需迁移；
- 顶层根对象同理持有 rootDek1 / rootDek2，整体以 KEK 加密。

### 路由与判定

- 新 / 编辑 / 移动子节点一律用活跃 `dek2`（`TreeContext.dekFor` 返回 dek2）；
- 触发点：`TreeContext.write` / `delete` 写后触发**父组**轮换；`NodeMover.moveGroup` /
  `moveEntry` 移动后触发**旧父组**轮换；
- 判定 `dek1` 是否仍被使用：**仅枚举本组直接子节点与其条目字段块**（读信封头 nonce/keyId
  反推 dekId 比较，不解密、不扫描全库）；软删除块仅打 `deleted:true` 标记、保留 parent 链路，
  故仍在 `childrenOf` 中，计入使用——但软删除走 `write` 会被重加密到 dek2（见下）；
- 若 `dek1` 已无任何直接子块 / 条目字段块使用（含被判定的软删除块），则丢弃 dek1：
  `dek2 → dek1`，随机新建 `dek2`，重写组块。

### 软删除落在 dek2（用户确认）

- 软删除 `TreeNode.markDeleted` 走 `write` → 用活跃 `dek2` 重加密；垃圾桶块落在 dek2 上，
  永远可读（dek2 提升为 dek1 后仍可读）；
- 因此「trash 里的算」在此方案下体现为：轮换判定必须枚举软删除块（已在 `childrenOf` 中），
  且 trash 在轮换后保持可读；但 trash 不钉住 dek1（它在 dek2 上），dek1 一旦无当前块使用即丢弃；
- 旧版已删数据的历史密文随 dek1 丢弃而不可恢复——前向保密更强。

### 并发安全

- `maybeRotateGroupKeys` 在 `write`/`delete` 释放锁之后、无锁状态下运行，会并发读写
  `Vault.groupDeks` 与 `KeyIdIndex`；故二者分别改为 `ConcurrentHashMap` 与全方法
  `synchronized`，消除并发 `get`/`put` 的 `ConcurrentModificationException`。

### 换主密码

- `MasterKeyRotator` 全程保留根对象 rootDek1/rootDek2 对不变，仅根对象块改以新 KEK 重加密、
  根级子块用 rootDek 重加密；组级 DEK 对同样逐组保留，仅外层父 DEK 改变时由其父组重写带入。

## 效果

- 前向保密：现存子块逐步迁移到新 dek2 后，旧 dek1 被丢弃，旧密文副本不可恢复；
- 渐进、无全库扫描：每次写/删/移只触发父组一次轻量判定；
- 格式兼容旧库（单 `dek`），无需迁移；
- 并发写入无 `ConcurrentModificationException`。

## 兼容性

**格式变更（写端）**：组 / 根对象块新增 `dek1`/`dek2`，移除 `dek`；**读端兼容**：单 `dek`
自动映射为 `dek1==dek2`。旧库可直接打开。
