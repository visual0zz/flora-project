# 设计：flora-sanctum 垃圾桶（虚拟根 + 三类异常节点）

日期：2026-08-26
主题：在主界面树中增加一个与数据根平级的"垃圾桶"虚拟根，集中展示三类异常节点（不可解锁 / 不可达 / 手动删除），手动删除以 JSON 内部标记实现，垃圾桶内容只读。

## 1. 概念与范围

垃圾桶是**纯展示用的虚拟根**，没有对应的物理存储（不写任何块）。它与"密码库"区段平级，挂在主树根节点 `全部` 之下。

垃圾桶内三类子节点：

1. **不可解锁（unlockable-failed）**：节点对应的 group DEK 在解锁阶段未被解开（父 group 的包裹 DEK 解密失败，或该节点块本身解密失败），导致无法读出内容。
2. **不可达（unreachable）**：节点块可解密，但其 `parent` 链指向一个不存在/未被发现的 uuid（孤儿），无法挂到正常树中。
3. **手动删除（manually-deleted）**：用户显式删除的节点。删除不再物理删除，而是在该节点 JSON 内写入 `"deleted": true` 标记；其**原有子节点不做任何修改**（仍指向被删父 uuid，从而仍能正常渲染为被删节点的子树）。

渲染规则：
- 被删节点在其父（垃圾桶）下渲染为普通节点；其内部子节点按原 `parent` 正常递归渲染（即垃圾桶里的 group 展开后能看到它原来的所有子节点）。
- 每个被删节点额外渲染一个只读字段「原位置」，值是根据其 `parent` 链临时计算的路径字符串（如 `密码库/A/被删组`）。
- 垃圾桶内所有节点**只读**：名称/字段不可编辑，无保存/删除/添加等操作按钮；保留查看（含密码复制、TOTP 显示等只读动作）。
- 必须为每类标注具体是"不可解锁 / 不可达 / 手动删除"哪一种。

## 2. 数据模型改动

### 2.1 删除标记（手动删除）

- `TreeNode` 新增 `boolean deleted()`：读 `data().getBool("deleted")`。
- 新增手动删除入口：
  - `GroupNode.markDeleted()` / `EntryNode.markDeleted()`：在自身 JSON 写入 `"deleted": true` 并 `ctx().write(...)` 回写；**不递归**改子节点。
  - `GroupNode.restore()` / `EntryNode.restore()`：去掉 `"deleted"` 标记并重写（移出垃圾桶）。
- 注意 `GroupNode.delete()`（物理递归删除）保留给"彻底删除"场景（如 GC），UI 的删除按钮改为调用 `markDeleted()`。

### 2.2 不可达 / 不可解锁的判断

新增 `TrashClassifier`（core 模块，无 UI 依赖）计算三类集合。判断依据：

- **不可解锁**：节点的 group（经 `TreeContext.parentGroupUuid` 求祖先组）在 `vault.groupDek(groupUuid) == null`，且节点块本身能定位（`Block` 存在）；或节点块本身解密失败（不在 `objects` 但 `blocks` 有）。
- **不可达**：节点块可解密（在 `objects`），但其 `parent` 字符串指向的 uuid 既不存在于 `objects`、也不是根对象 uuid，且其祖先链上没有任何可达节点（复用 `GarbageCollector` 的可达集思想，只判不删）。
- **手动删除**：`deleted() == true`。

`TrashClassifier.classify()` 返回 `TrashView`，含三个 `List<UUID>`（unlockable / unreachable / manual）以及按 uuid 缓存的「原位置」路径计算。

为避免与物理 `GarbageCollector` 冲突：不可达判定**只报告不删除**（不改 `collect()`）。

## 3. UI 改动（SanctumGui）

### 3.1 树结构

`rebuildGroupTree()` 在 `密码库` 区段之后新增一个 `DefaultMutableTreeNode` 区段节点：
- `userObject = NodeType.TRASH`（新增枚举值，仅作区段标记，不进入任何真实树）。
- 图标 `ui/trash`（24px）；展开后列出三类子节点：可为三类各建一个子分组节点（不可解锁 / 不可达 / 手动删除），也可以平铺。采用**三类各一个分组**更清晰。
- 三类子分组下挂对应 uuid 节点（`userObject = UUID`），与现有 group/entry 渲染复用；但若 uuid 原节点类型未知（不可解锁块无法解密）则降级为「未知节点」展示 uuid 前 8 位。

### 3.2 渲染器

`FolderTreeRenderer` 增加对 `NodeType.TRASH` 分支：文本"垃圾桶"，图标 `ui/trash`；对三类子分组显示对应中文名 + 各自图标（复用 `ui/folder` 或 `ui/trash` 变体，详见 3.4）。

### 3.3 详情面板（只读）

`showSelectedEntry()` / `renderGroupPanel` / `renderEntry` 需感知"当前选中的是垃圾桶节点"：
- 新增 `isTrashSelection()`：选中节点在三类 uuid 集合中。
- 垃圾桶节点详情面板：调用只读渲染 `renderTrashNode(uuid, reason)`：
  - 标题显示类型标签（不可解锁 / 不可达 / 手动删除）。
  - 渲染名称 + 各字段（复用现有行组件，但 JTextField 设为 `setEditable(false)`，且不挂任何保存/删除/添加按钮）。
  - 额外只读行「原位置」=`TrashView.originalPath(uuid)`。
  - 允许只读查看密码/TOTP（通过临时解密，若不可解锁则显示「无法解密」）。

### 3.4 新图标

新增 `ui/trash-unlock.svg`、`ui/trash-unreachable.svg`、`ui/trash-deleted.svg` 三个图标（或用单一 `ui/trash` + 文字标签区分）。优先方案：三类分组节点各用一个带角标/颜色的变体 SVG，文本始终标注类型，避免歧义。

## 4. 不改动项

- 物理删除链路（`TreeNode.delete`、`GarbageCollector.collect`、`MarkdownObjectStore.delete`）保持不变。
- `keyId`、DEK 派生、解锁流程、存储格式不变。仅新增可选 JSON 字段 `"deleted"`（旧库无此字段即视为未删）。

## 5. 验收

- 新建 group A → 在 A 下建 group B 与 entry E。
- 手动删除 A：A 与 B、E 仍在存储中；A 出现于垃圾桶「手动删除」；展开 A 可见 B、E；A/B/E 详情只读且显示「原位置」与类型。
- 模拟不可达：手工改某 entry 的 `parent` 指向随机 uuid → 该 entry 出现在「不可达」。
- 模拟不可解锁：使某 group 的包裹 DEK 无法解开（测试构造）→ 出现在「不可解锁」。
- 全部 53 个现有测试通过，新增分类器单测。
