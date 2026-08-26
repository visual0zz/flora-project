# 决策：拆分 NodeType 为存储节点与展示节点两个枚举

日期：2026-08-26
模块：flora-sanctum
编号：01

## 背景

原 `NodeType` 单一枚举同时承载两类语义：

1. **存储节点类型**：JSON 负载的 `type` 字段值（manifest/root/group/entry/field/
   customField/config/icon/sshKey/remote），持久化到存储块，参与解锁/树构建/可达性判定。
2. **展示节点类型**：纯 UI 区段标记（GROUP/ICON/SSH_KEY/REMOTE 被复用为左树区段，
   TRASH 为垃圾桶虚拟根），不对应任何真实存储对象。

两类混在一个枚举里导致：区段标记与可存储类型无法在类型层面区分；新增展示概念
（如垃圾桶）只能硬塞进同一枚举并加注释"仅作区段标记"；`TreeNode.type()` 与 UI 区段
取值是同一个类型，调用方易误用。

## 决策

拆分为两个独立枚举：

- **`StoredNodeType`**（存储节点）：保留原 JSON `type` 取值全集，新增 `view()` 字段指向
  其展示分类（`ViewNodeType`）。即"存储节点内部有个字段指定自己的展示节点是什么"。
- **`ViewNodeType`**（展示节点）：仅含 UI 语义——`PASSWORD(密码库)`、`ICON(图标)`、
  `SSH_KEY(SSH 密钥)`、`REMOTE(远程)`、`TRASH(垃圾桶)`、`SETTINGS(设置)` 等纯区段/
  虚拟根标记，不绑定任何存储 type 字符串。

`StoredNodeType.view()` 返回对应的 `ViewNodeType`（如 GROUP/ENTRY/FIELD/CUSTOM_FIELD →
PASSWORD；ICON → ICON；SSH_KEY → SSH_KEY；REMOTE → REMOTE；CONFIG → SETTINGS；
ROOT/MANIFEST 无展示归属可用 `Optional`）。

## 影响

- `TreeNode.type()` 返回 `StoredNodeType`；`TreeContext`/`ObjectTree`/`*Tree` 的
  `belongsTo`/`category`/`find` 改用 `StoredNodeType`。
- `DataTree.category()` 类型由 `NodeType` 改为 `ViewNodeType`（区段即展示概念）。
- UI 区段节点 userObject 改用 `ViewNodeType`；`sectionOf`/`sectionDisplayName`/
  `FolderTreeRenderer` 全面切换。
- `Sanctum.tree(ViewNodeType)`、`tree(NodeType)` 重载改为按 `ViewNodeType` 取树。
- 序列化 `fromTag`/`tag()` 只存在于 `StoredNodeType`；`ViewNodeType` 不落盘。

## 理由

概念单一职责：存储格式与 UI 展示解耦，新增展示概念不必污染存储枚举；类型系统可在编译期
区分"这是存储对象"还是"这是 UI 区段"，避免 `sectionOf` 与 `typeOf` 共用一个枚举的歧义。
