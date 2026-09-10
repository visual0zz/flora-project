# 设计：新增 category 节点类型，作为 root 与顶级对象之间的"数据类分隔层"

日期：2026-09-10
模块：flora-sanctum-core（model / crypto / vault）
状态：设计稿（待决策评审后实现）

> 2026-09-10 修订：放弃"父子指向翻转为父→子"的设想，维持现有**子→父**模型（每个节点块内持 `parent` 字段），category 层建立在子→父之上。理由见文末"权衡与风险"。

## 背景与动机

当前 sanctum 是**单根扁平模型**：唯一根对象（`type=root`，持 rootDek）之下直接平铺四类数据对象——

- 密码类：`group` / `entry` / `field`（`parent` 指向根对象或某 group）
- 图标类：`icon`
- 密钥类：`sshKey`
- 远程类：`remote`

`icon` / `sshKey` / `remote` 三类当前都**直接以 rootDek 加密**（`IconTree.createIcon` / `SshKeyTree.createSshKey` / `RemoteTree.addRemote` 均 `writeWithDek(..., vault.rootDek())`，`parent=根对象uuid`），造成两个结构性问题：

1. **前向保密粒度太粗**：rootDek 是"全库兜底密钥"，换主密码时 rootDek 值不变、仅根块改以新 KEK 重加密（见 `MasterKeyRotator`）。一旦 rootDek 因任一子块的旧 keyId 长期驻留于 KeyIdIndex，整库四类数据共用同一把活跃密钥，无法按类隔离轮换。
2. **特殊分支散落**：icon/sshKey/remote 绕过 `dekFor` 直接拿 rootDek，与 group 的"父组 DEK"模型不一致，逻辑不统一。

目标：在 **root 与顶级对象之间插入一层 `category` 节点**，每类数据拥有自己的 category 节点（含独立 DEK 对 + 惰性轮换），使"容器级加密粒度 = 对象"的模型对全部数据类统一，并获得**按类独立的前向保密**。

> 本次**维持子→父模型**：每个数据对象仍在其块内写 `parent`（指向所属 category 或 group），category 节点本身 `parent=根对象uuid`。父级不持有子列表，列子靠 `TreeContext.childrenByParent` 内存索引（`TreeContext.java:42-43`）。增/删/移一个子节点只重写该子节点块（父块不动，见 `TreeContext.delete` 仅删子块 + 维护内存索引，`TreeContext.java:245-267`）。

## 目标

- 新增 1 个存储类型 `CATEGORY("category", null)`，内部以字段 `category=xxx` 区分数据类
  （`password` / `icon` / `sshKey` / `remote`）。
- 层级变为 `root → {category:password, category:icon, category:sshKey, category:remote} → 各数据对象`。
- 每个 category 节点持有自己的 DEK 对（`dek1`/`dek2`，明文 base64 存于块内），外层以 rootDek 加密
  （因 `parent=根对象uuid`）；其下数据对象以该 category 的活跃 DEK 加密（`parent=category uuid`）。
- 复用现有 `TreeContext.maybeRotateGroupKeys` / `dekFor` / `write`/`delete` 触发机制，使 category 的
  密钥轮换与级联行为与 group 完全同源。
- **不做前向兼容迁移**（用户明确放弃旧库）：旧格式（数据对象 `parent==root`、根对象无 `categories` 字段）
  由解锁器显式拒绝，避免半解锁静默损坏。

## 方案概述

### category 节点块结构

```
type     = "category"
category = "password" | "icon" | "sshKey" | "remote"   // 数据类区分符
parent   = 根对象 uuid
dek1     = <base64>   // 退役中
dek2     = <base64>   // 活跃（新写入子节点用）
order    = <小数索引>  // 同类内顺序（UI 通常不展示 category 本身，可省略）
```

category 节点自身用 **rootDek** 加密（外层保护），与 group 块结构同构；其 `dek1/dek2` 是该类数据的
密钥对，值随惰性轮换变化（见下）。category 在 `ViewNodeType` 上无展示归属（`null`），等同 `ROOT`，
不被任何数据树 `find` 返回，UI 不直接呈现。

### 复用现有密钥机制（关键：几乎零新增逻辑）

以下机制本就按"任意 uuid 持有密钥对"泛型实现，category 注册 `addGroupDek(categoryUuid, …)` 后即复用：

- `TreeContext.dekFor(groupId)`：返回 `vault.groupDek(groupId)`；category 的孩子传
  `groupId=categoryUuid` 即取该类 DEK。
- `TreeContext.maybeRotateGroupKeys(groupUuid)`：检查 `vault.groupKeys(categoryUuid)`，
  枚举 category 直接孩子判定 `dek1` 是否失活 → 轮换 → `rewriteGroupKeys`。对 category 完全适用；
  轮换 category 时经 `ctx.write` 顺带触发 `maybeRotateGroupKeys(root)`（上级联，到 root 经
  `writeWithDek` 终止，**有界、安全**）。
- `TreeContext.write/delete`：写/删 category 下孩子会触发 `maybeRotateGroupKeys(categoryUuid)`，
  自动获得按类隔离的前向保密。
- `TreeNode.markDeleted/restore`：`groupId=parentGroupUuid(d)`，父为 category 也能正确取 DEK。
- `GarbageCollector`：根可达集 = manifest + root + `parent==root` 的 category 节点，其下数据经
  parent 边可达——**无需改动**。
- `MasterKeyRotator.migrateRootLevelBlocks`：换主密码后 `parent==oldRoot` 的只剩 category 节点
  （依旧用 rootDek 可解、DEK 值不变），数据对象 `parent==category` 自然跳过。逻辑**无需改**，且
  工作量反而更小；建议仅补注释说明新语义。

### category uuid 来源（本次拍板：存于根对象）

不引入新的确定性派生原语（如 `RootUuid.derive` 的 category 变体），而是**在创建库时随机生成各
category 的 uuid，并写入根对象 JSON 的 `categories` 映射**：

```
// 根对象新增字段
categories = {
  "password": "<hex32>",
  "icon":     "<hex32>",
  "sshKey":   "<hex32>",
  "remote":   "<hex32>"
}
```

- 解锁时 `discoverRootDeks` 先读根对象 → 拿到 `categories` → 登记各 category uuid；
  随后既有的"逐层发现 DEK"循环（`VaultUnlocker.java` 内 `type==GROUP` 分支）在解密 category 块
  （rootDek 包外层）后，对其 `dek1/dek2` 调用 `addGroupDek(categoryUuid, …)`，使该类 DEK 就绪。
- 优点：O(1) 定位（读根对象即得，免扫描）、无新原语、uuid 稳定可跨重载复用；旧格式（无 `categories`
  字段）直接拒绝解锁。
- 该循环当前仅处理 `type==GROUP`，需扩展为 `GROUP || CATEGORY`。

### 各模块改动点（实现阶段）

1. **`StoredNodeType`**：加 `CATEGORY("category", null)`（1 行）。
2. **`Vault`**：新增 `categoryUuid(String disc)` / `addCategory(String disc, UUID uuid)`（内存映射，
   解锁时从根对象填充）；`clearSecrets` 一并清理。
3. **`VaultCreator`**：`writeRootGroup` 之后，生成 4 个 category uuid（随机），各写一块
   （`writeCipherBlock(categoryUuid, json, rootDek, created)`，块内含 `dek1/dek2` 随机对 +
   `category=xxx`），并把 `categories` 映射写进根对象 JSON。
4. **`VaultUnlocker.discoverRootDeks`**：
   - 读根对象后解析 `categories` 映射并 `addCategory`；**无该字段即抛旧格式拒绝
     `VaultUnlockException`**；
   - DEK 发现循环接受 `type==CATEGORY`（同 `GROUP` 的 `readGroupKeys` → `addGroupDek`）。
5. **`DataTree`**：`roots()` 改为 `childrenOf(ctx.vault().categoryUuid(discriminator(view())))`，
   经 `ViewNodeType → discriminator` 静态映射（PASSWORD→password / ICON→icon / SSH_KEY→sshKey /
   REMOTE→remote）取 category uuid。
6. **`ObjectTree`**：
   - `createGroup(null)` / `createEntry(null)` 的 `effectiveParent` 改为 `categoryUuid("password")`；
   - `parentOf` / `isTopLevel` / `rootGroups` / `rootEntries`：把"父是某 category uuid"视为顶层边界
     （新增 `isCategoryUuid(uuid)` 辅助）。
7. **`IconTree.createIcon` / `SshKeyTree.createSshKey` / `RemoteTree.addRemote`**：
   `parent=categoryUuid(本类)`，`ctx.write(uuid, json, categoryUuid)`（经 `dekFor` 取该类 DEK），
   弃用 `writeWithDek(rootDek)`。
8. **`SshKeyTree.reorder` / `RemoteTree.reorder`**：排序的"父"由根对象改为本类 category uuid
   （复用 `appendOrder(categoryUuid)` 与同类 sibling 序计算，原 `computeRootSiblingOrder` 需
   category 作用域化）。
9. **`NodeMover`**：
   - `moveGroup`：新父允许 `type==CATEGORY`（至少 password category），与 `GROUP` 并列；
   - `moveEntry`：新父允许 `type==CATEGORY`（password）与 `GROUP`，移除"`ROOT` 即顶层"的特判
     （顶层条目现指落在 password category 下，加密走该类 DEK）；
   - 对应注释更新。
10. **`FieldNode.groupIdOf`**：条目→父条目→父组/category，泛型路径不变，**无需改**。
11. **`Sanctum` 门面 / `SanctumGui`**：若顶层辅助直接锚 root 需改；`findNode` 对 category uuid 返回
    null（数据树 `belongsTo` 不命中 category），move/purge/restore 对 category uuid 显式拒绝
    （category 为内部结构、不可用户移动）。
12. **`CONFIG` / `MANIFEST`**：本期不在 category 范围内——`CONFIG` 维持现状（仓库级设置），
    `MANIFEST` 为明文引导块。后续可归入 `settings` 类，不在本次。

### 前向兼容

不做。解锁器检测根对象无 `categories` 字段即判定旧格式并拒绝（明确异常，非静默空库）。
旧库迁移路径：导出 → 新建库（新格式）→ 导入。

## 影响面（文件清单）

core（model / crypto / vault）：`StoredNodeType`、`Vault`、`VaultCreator`、`VaultUnlocker`、
`TreeContext`（仅注释/语义确认，逻辑已泛型）、`DataTree`、`ObjectTree`、`IconTree`、
`SshKeyTree`、`RemoteTree`、`NodeMover`、`FieldNode`（无需改）、`GarbageCollector`（无需改）、
`MasterKeyRotator`（仅注释）。

app（ui）：`SanctumGui` 若直接读 root 顶层需改；数据访问经树 `roots()` 时基本无感。

测试：新增 `VaultCreator`+`VaultUnlocker` category 往返、`CategoryTreeRoutingTest`
（各树 `roots()` 落 category 下、move 顶层重加密字段、category 轮换级联与 root 边界、
GC 不误删 category 子树、旧格式拒绝解锁）。core/app 既有测试应全绿（行为等价，仅多一层间接）。

## 风险与踩坑（预判）

- **发现顺序**：category 块须先于其孩子被发现。根对象读出 `categories` + rootDek 先登记，
  category 块（rootDek 外层）即可解密并登记其 DEK，孩子在后续迭代解密——既有 `while(any)` 循环
  已保证，仅需把 `CATEGORY` 纳入发现分支。
- **轮换级联上界**：category 轮换 → `maybeRotateGroupKeys(root)` → 可能 root 轮换 →
  `rewriteGroupKeys(root)` 走 `writeWithDek`（终态）。有界，不会无限。
- **reorder 作用域**：icon/sshKey/remote 的排序"父"必须改为 category uuid，否则排序与加密父不一致。
- **category 自身块加密**：务必用 rootDek（因其 `parent=root`），不能误用类别 DEK，否则换主密码时
  根块重加密但 category 块无法以新 KEK 定位（category 块不由 KEK 直接加密，而由 rootDek 加密，
  rootDek 值不变，故安全）。

## 权衡与风险（价值评估，决策前必读）

1. **安全收益边际。** 两个动机里，(2) 是代码整洁度诉求而非安全诉求；(1) "按类独立轮换"要真正生效，
   得把某一整类删光使其 DEK 退役——对 icon/sshKey/remote 几乎不会发生。且 rootDek 在换主密码时
   不变是**既有设计**（`MasterKeyRotator` 只重加密根块），这一点即使不加 category 也成立。
   核心收益是模型一致性，而非显著的安全提升。

2. **改动面比文档显式列出的更宽（导航层，非 crypto 层）。** crypto/密钥发现确为"近乎零新增逻辑"，
   但真正的成本在**最易出 bug 的导航层**：涉及 `rootDek()`/`writeWithDek` 的调用点散布于 9 个文件、
   约 20+ 处；涉及"顶层 / root 为父"判定的引用全模型约 68 处（`parentOf`/`isTopLevel`/`rootGroups`/
   `rootEntries`/`computeRootSiblingOrder`/`childrenOf(root)` 等）。顶层边界、`NodeMover` 环检测与
   权限、`reorder` 的 sibling 序计算、`roots()` 锚点，只要漏改一处就会出现节点不可见、双重列出、
   移动后加密父不一致等隐蔽回归。文档"几乎零新增逻辑"对 crypto 成立，对导航层不成立。

3. **破坏性变更代价被低估。** 文档说"不做前向兼容迁移，旧格式解锁即拒绝"。但当前 icon/sshKey/remote
   的 `parent==root` 正是文档定义的"旧格式"——一旦落地，所有现存 vault（含测试库与任何真实库）全部
   打不开。"导出→新建→导入"的迁移路径本身要先能在新旧两种格式间读取，等于又写一遍兼容代码，与
   "不做迁移"自相矛盾。若 sanctum 尚无真实用户可接受，但应在文档顶部显著标注，而非埋于末尾。

4. **与并行工作交叉。** category 层改变 `parent` 指向（数据对象 `parent` 由 root 变 category），
   云同步的 merge 依赖 `parent` 引用；`SanctumGui` 的 doSync reopen（KEK 重开路径）、trash 视图、
   virtual sections 都要随之验证逻辑。KDBX 导入器若某处硬编码 `parent==root`，会静默出错。

5. **更便宜的替代。** 若目标仅为"消除 rootDek 特殊分支、统一模型"：保留 icon/sshKey/remote 的
   `parent=root`，但让它们经 `dekFor` 取 DEK 时复用 root 的 keyId（本就在 rootDek 索引里）。代码
   统一性基本达成，零格式变更、零导航层改动，成本不足本方案的 1/10。

## 推荐决策

- 现阶段：**不实现**。把本文档状态维持为"设计稿/备选"，顶部显著标注"破坏性、无迁移"。
- 若仅为整洁：走上面的"最小代价方案"。
- 若确要按类前向保密：排到功能稳定、格式冻结之后；届时补"旧库兼容读取"迁移实现（至少导入器能读旧
  格式），别做硬性拒绝。

## 测试建议

1. 新建库即含 4 个 category 块、根对象含 `categories`；解锁后 `vault.categoryUuid(...)` 可解析，
   且 `groupDek(categoryUuid)` 非空。
2. 各类树 `roots()` 返回的是其 category 的直接孩子，而非 `childrenOf(root)`。
3. 在 password category 下建组/条目，在 icon category 下建图标；验证块 `parent` 与外层加密 DEK。
4. 反复编辑/删除某 category 下对象直至触发该 category 惰性轮换，断言其下子块 keyId 变更为该类新
   dek2，且其它 category 不受影响（隔离）。
5. 移动条目到顶层（password category），断言条目块 + 全部字段块重加密到该类 DEK。
6. GC：构造孤立块，断言只删孤立块、category 与其子树可达不误删。
7. 换主密码后，category 块 `parent` 指向新根 uuid、其值不变、子类数据可解。
8. 旧格式库（无 `categories`）解锁被拒。
