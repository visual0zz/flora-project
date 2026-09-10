# 决策：新增 category 节点类型，作为 root 与顶级对象之间的数据类分隔层

日期：2026-09-10
模块：flora-sanctum-core（model / crypto / vault）
关联设计：`addition/design/idea20260910-01-sanctum-category.md`

## 背景

当前 sanctum 为单根扁平模型：根对象（`type=root`，持 rootDek）之下直接平铺 `group`/`entry`/
`field`/`icon`/`sshKey`/`remote`。`icon`/`sshKey`/`remote` 三类直接以 rootDek 加密
（`IconTree.createIcon` 等 `writeWithDek(..., vault.rootDek())`），带来两点问题：

1. **前向保密粒度太粗**：rootDek 是整库兜底密钥，换主密码时值不变，四类数据无法按类隔离轮换。
2. **特殊分支散落**：icon/sshKey/remote 绕过 `dekFor` 直接拿 rootDek，与 group 的"父组 DEK"模型不一致。

## 决策

1. **新增 1 个存储类型 `CATEGORY("category", null)`**（基础设施，无展示归属，同 `ROOT`）。
   内部以字段 `category=xxx` 区分数据类：`password` / `icon` / `sshKey` / `remote`。

2. **插入一层 category 节点**：层级由 `root → 数据对象` 变为
   `root → {category:password, category:icon, category:sshKey, category:remote} → 各数据对象`。
   category 节点 `parent=根对象uuid`、外层以 **rootDek** 加密，块内含独立 `dek1`/`dek2` 明文对，
   其下数据对象以该类活跃 DEK 加密。

3. **category 复用现有 DEK / 惰性轮换机制，不新增轮换逻辑**：注册 `addGroupDek(categoryUuid, …)`
   后，`TreeContext.dekFor` / `maybeRotateGroupKeys` / `write`·`delete` 触发 / `rewriteGroupKeys`
   对 category 与 group 同源生效，获得**按类独立的前向保密**与向上级联（到 root 经 `writeWithDek`
   终止，有界）。

4. **category uuid 存于根对象 JSON 的 `categories` 映射**（创建时随机生成），不引入新的确定性派生
   原语：解锁 O(1) 读取、uuid 稳定；`VaultUnlocker` 的 DEK 发现循环扩展为接受 `type==CATEGORY`
   （同 `GROUP` 的 `readGroupKeys` → `addGroupDek`）。

5. **不做前向兼容迁移**：解锁器检测根对象无 `categories` 字段即判定旧格式并**显式拒绝**
   （`VaultUnlockException`），避免半解锁静默空库；旧库走"导出→新建库→导入"。

## 关键设计点

- **层级与加密归属**：category 块自身用 rootDek（因 `parent=root`）；其孩子用该类 DEK。
  换主密码时 rootDek 值不变、category 块仍可解，仅 `parent` 改指新根 uuid——`MasterKeyRotator`
  逻辑无需改、工作量更小（只迁 category 块）。
- **各树的"顶层"锚点**：`DataTree.roots()` 改为 `childrenOf(categoryUuid(discriminator(view())))`，
  `ObjectTree` 顶层 `createGroup(null)`/`createEntry(null)` 落 `password` category；
  `parentOf`/`isTopLevel`/`rootGroups` 以"父是某 category uuid"为顶层边界。`CONFIG`/`MANIFEST`
  不在本次 category 范围。
- **移动语义**：`NodeMover.moveGroup` 允许新父为 `CATEGORY`（password）；`moveEntry` 允许新父为
  `CATEGORY` 或 `GROUP`，移除"`ROOT` 即顶层"特判；条目降到顶层即落 password category，字段随条目
  重加密到该类 DEK。
- **GC / `FieldNode` / `TreeNode`**：无需改动（category `parent==root` 可达，孩子经 parent 边可达；
  `FieldNode.groupIdOf` 泛型路径不变）。
- **安全边界（沿用已分析）**：category 轮换级联向上、`maybeRotateGroupKeys(root)` 之后 root 轮换
  经 `writeWithDek` 终止，无无限循环、受树深限制、在同一 `ReentrantLock` 内原子。

## 影响

- core：`StoredNodeType`（+CATEGORY）、`Vault`（category uuid 映射）、`VaultCreator`（建 4 类
  category + 写 `categories`）、`VaultUnlocker`（读 `categories` + 发现循环纳入 CATEGORY + 旧格式
  拒绝）、`DataTree`/`ObjectTree`/`IconTree`/`SshKeyTree`/`RemoteTree`（锚到 category）、`NodeMover`
  （父类型允许 CATEGORY）、`TreeContext`/`GarbageCollector`/`MasterKeyRotator`（仅注释/语义确认）。
- app：`SanctumGui` 经树 `roots()` 基本无感；`findNode` 对 category uuid 返回 null，move/purge/
  restore 显式拒绝对 category 操作。
- 测试：新增 category 往返、`CategoryTreeRoutingTest`（路由/移动重加密/轮换隔离/GC/旧格式拒绝）；
  既有 core/app 测试行为等价、应全绿。

## 决策（补充）：旧格式处理

解锁阶段在 `discoverRootDeks` 解析根对象时，若缺 `categories` 字段，直接抛
`VaultUnlockException`（阶段 `ROOT_INCOMPLETE` 或新增 `LEGACY_FORMAT`），不让旧库进入半解锁状态。
