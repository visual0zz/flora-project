# 审查：sanctum 数据存储结构与 DEK/父节点一致性

- 审查日期：2026-08-26
- 模块：`flora-sanctum`（core）
- 审查对象：块存储引擎、密钥层次、解锁流程、DEK 路由
- 关键文件：
  - `flora-sanctum-core/.../model/impl/TreeContext.java`
  - `flora-sanctum-core/.../model/vault/VaultUnlocker.java`
  - `flora-sanctum-core/.../model/vault/Vault.java`
  - `flora-sanctum-core/.../model/tree/ObjectTree.java`
  - `flora-sanctum-core/.../model/tree/FieldNode.java`
  - `addition/doc/wiki/sanctum/04-存储设计.md`

## 背景

sanctum 的数据存储为「UUID 路径寻址的对象库 + 一文件一块的 base58 信封」。密钥层次：

```
master password → Argon2id → KEK → 包裹 root DEK
root DEK → 包裹子文件夹 DEK（父 DEK 包子 DEK，可任意深度）
每个 DEK → HKDF-SHA256 → encKey → AES-256-GCM-SIV 加密其归属对象
```

正常路径下，一个 group 的三要素（块加密 DEK / `dek` 字段包裹 DEK / `parent` 字段）在 `ObjectTree.createGroup`（`ObjectTree.java:90-99`）中由同一个 `parentId` 推导，保持完全一致：

- `parentDek = folderDek(parentId) ?? data 根 DEK`
- group 块用 `parentDek` 加密（`write(groupUuid, group, parentId)` → `dekFor(parentId)`）
- `dek` 字段用同一个 `parentDek` `wrapDek`
- `parent` 字段写入 `parentId`

读取/写入路由 `TreeContext.dekFor(groupId)`（`TreeContext.java:138-143`）按 `parent` 解析出的 group uuid 查 `folderDeks`；解锁 `VaultUnlocker.discoverRootDeks`（`VaultUnlocker.java:64-133`）按「哪个已知 DEK 能解开 group 的 `dek` 字段」登记 `folderDeks[块自身 uuid]`。

## 发现的问题

### 问题 1：写入路径每次全量扫描求最大时间戳（性能）

`TreeContext.nextTimestamp()`（`TreeContext.java:153-161`）每次写对象都 `store.scan()` 遍历全库块求最大时间戳，时间复杂度 O(N)。当库内对象数量很大（上千级）时，每次保存一个字段都要全量扫描一遍。

- **建议**：`scanAll()`（`TreeContext.java:40`）重建内存图时已遍历过全部块，可在那时缓存 `maxTs` 到内存字段，写入后就地更新，`nextTimestamp()` 直接返回缓存值，避免重复全扫。
- 严重性：低（性能，随库规模放大）。

### 问题 2：`blockOf` 缓存 miss 时二次全量扫描（冗余）

`TreeContext.blockOf()`（`TreeContext.java:56-68`）在 `blocks` 缓存未命中时再次 `store.scan()`。但 `scanAll` 已把全部可解密的块放入 `blocks`，绝大多数调用路径下这第二次扫描是多余的。

- **建议**：新写入块时在 `write` 中就地记录块定位，避免缓存 miss 时回退到全量扫描。
- 严重性：低（性能/冗余）。

### 问题 3：parent 引用与 dek 包裹链是两条独立通道，缺乏一致性校验（核心隐患）

解锁流程 `discoverRootDeks`（`VaultUnlocker.java:64-133`）完全不读 `parent` 字段，只依据「哪个已知 DEK 能解开 group 的 `dek` 字段」按块自身 uuid 登记 `folderDeks`。而读取/写入路由 `dekFor`（`TreeContext.java:138-143`）按 `parent` 查 `folderDeks`，查不到时**静默 fallback 到 data 根 DEK**。

隐患链条：

1. 若某 folder DEK 在解锁时未被登记（包裹链断开），向其子树写新数据会经 `dekFor` 静默用根 DEK 加密。
2. 结果：该节点早期子节点（用其 folder DEK 加密）不可读，新写入子节点（用根 DEK 加密）可读 —— 形成「密钥域错配 + 部分可读部分不可读」的状态。
3. 全程无异常抛出，问题被悄悄吞掉并随后续写入进一步恶化。

- **建议**：
  - 解锁后（或解锁完成、`TreeContext` 构建后）增加一次一致性校验：遍历每个 group，确认其 `parent` 所指 folder DEK 与实际包裹其 `dek` 字段的 DEK 一致；发现不一致时输出告警/错误而非静默继续。
  - `dekFor` 在 `folderDek(groupId) == null` 时应显式抛错或告警，而不是静默回退根 DEK（静默回退掩盖了数据一致性损坏）。
- 严重性：高（静默数据损坏 + 可用性陷阱）。

### 问题 4：单块 ≤ 64KB 上限、无分块规则

设计文档（`04-存储设计.md:195`）明确：一块 = 一个 base58 串，建议 ≤ 64KB 原始字节；当前适配器全对象单块，大对象（大附件、长 note）的分块规则暂未启用。

- **建议**：为逻辑对象引入内容分块 + 叶子索引（nonce/keyId 仍按块生成），解除单块体积上限。
- 严重性：中（功能限制，影响大对象场景）。

### 问题 5：解锁每次重建全内存图，无持久索引缓存

`TreeContext.scanAll`（`TreeContext.java:40`）每次解锁都全量解密建内存图，无增量、无加密索引缓存。大库解锁慢。

- **建议**：可选方案是用 KEK 包裹一个索引缓存块（保存 folder DEK 映射与 maxTs）加速解锁。需权衡复杂度与攻击面，非必须。
- 严重性：低（性能，随库规模放大）。

### 问题 6：非原子写，未启用 git 时半写风险

设计承认单块覆盖写、崩溃中间态靠 git 兜底（`04-存储设计.md:182`）。未启用 git 时，半写文件可能损坏。

- **建议**：写路径改为「写临时文件 + 原子 rename」降低损坏概率（多数平台 rename 为原子操作）。
- 严重性：中（数据安全，未启用 git 同步时暴露）。

### 问题 7：GC 可达性与解密可用性解耦

`GarbageCollector` 沿 `parent` 边遍历可达性（`04-存储设计.md:144`），与「按 dek 能否解密」是两套独立逻辑（见问题 3）。若 `parent` 与 dek 包裹链同时被改坏，会出现「按 parent 可达但按 dek 不可解密」的悬空对象被 GC 保留。

- **建议**：GC 遍历时一并校验可达对象的可解密性，对不可解密且无法恢复的对象单独标记/报告。
- 严重性：低（与问题 3 同源，独立出现概率低）。

## 手动配置「节点依赖的解锁 DEK 与 parent 节点不一致」能否正常解锁

结论取决于「不一致」的具体改法。因为块加密 DEK、`dek` 包裹 DEK、`parent` 字段在正常路径下由同一 `parentId` 推导，手动配置分两种情形：

### 情形 A：只改 `parent` 字段，不动 `dek` 包裹

例如把 group G 的 `parent` 从 B 改成 A，但 G 的块密文与 `dek` 字段仍由 B 的 DEK 包裹。

- 解锁**完全正常**：`discoverRootDeks` 不读 parent，用 B 的 DEK 解开 G 的块、`unwrap` 出 G 的 folder DEK，登记到 `folderDeks[G.uuid]`。G 及其子树照常读写。
- 影响仅限：树中 G 显示在 A 之下（显示层级错误），以及 GC 沿 parent 边把 G 挂到 A 下。加解密不受影响。

### 情形 B：把 `dek` 字段重裹成与块加密 DEK 不一致的密钥

例如 G 的块仍用 B 加密，但 `dek` 字段被重裹成某个解锁时无法发现的 DEK（已删除的父、或随机未知密钥）。

- 解锁**不会报错也不会崩溃**：`tryDecode`/`unwrap` 失败仅返回 null 并 `continue` 跳过（`VaultUnlocker.java:109-128`、`154-162`）。G 的块本身仍能用 B 的 DEK 解开（可见），但 `folderDeks[G.uuid]` **不会被登记**。
- 后果：G 的子树（用 G 的 folder DEK 加密）**无法解密**；后续往 G 写新数据时 `dekFor(G.uuid)` 查不到 → **静默 fallback 到 data 根 DEK**（`TreeContext.java:142`），造成「部分子节点用根 DEK（可读）、早期子节点用 G 的 DEK（不可读）」的密钥域错配，全程无任何异常。
- 这正是问题 3 描述隐患的触发路径。

### 总体结论

- 解锁流程是「密码学驱动、容错」的，不会因为 parent 与包裹链不一致而失败或抛错。
- 能否「正常解锁并可读」取决于：该节点的 `dek` 密文**是否仍能被某个解锁时可发现的父链 DEK 解开**。
  - 能被解开（情形 A）→ 一切正常，`parent` 配错无密码学影响。
  - 不能被解开（情形 B）→ 解锁不报错，但该节点子树不可读，且后续写入被静默错配到根 DEK。
- 根本症结：缺少「parent ↔ dek 包裹链」一致性校验，且 `dekFor` 静默回退根 DEK，使手动损坏被悄悄吞掉并进一步恶化。建议落地问题 3 的校验与告警。

## 优先级排序

| 优先级 | 问题 | 类型 |
|--------|------|------|
| 高 | 问题 3：parent/dek 包裹链无校验 + `dekFor` 静默回退根 DEK | 数据一致性/健壮性 |
| 中 | 问题 4：单块 64KB 上限、无分块 | 功能限制 |
| 中 | 问题 6：非原子写、未启用 git 时半写风险 | 数据安全 |
| 低 | 问题 1：写入每次全量扫描求 maxTs | 性能 |
| 低 | 问题 2：`blockOf` 二次全量扫描冗余 | 性能 |
| 低 | 问题 5：解锁无持久索引缓存 | 性能 |
| 低 | 问题 7：GC 可达性与解密性解耦 | 一致性（与问题 3 同源） |

## 建议的下一步

1. 落地问题 3：解锁后一致性校验 + `dekFor` 缺失显式告警（最高收益，堵住静默损坏）。
2. 评估问题 6 的「临时文件 + 原子 rename」写入，提升未启用 git 时的健壮性。
3. 视大对象需求决定是否启动问题 4 的分块方案。
