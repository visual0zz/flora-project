# 决策：拖拽改归属的 DEK 重路由方案（NodeMover）

日期：2026-08-30
模块：flora-sanctum-core（NodeMover）/ flora-sanctum-app（拖拽 TransferHandler）

## 背景

用户要求：左树支持拖拽改变归属；中间栏的条目/文件夹可拖放到中间栏或左树展示的文件夹中。
数据模型里每个 Group 有独立 DEK，由父 Group 的 DEK 包裹（存于 `dek` 字段）；条目/字段块用
"所属组 DEK"加密。块能否解密只取决于块头内嵌 `keyId`（绑定加密时所用 DEK），与逻辑 `parent` 无关。

## 决策

引入 `NodeMover`（core `model.impl`），`Sanctum.move(node, newParent)` 委托之：

- **移动组**：仅重加密该组自身对象块（改用新父 DEK），并**同步**用新父 DEK 重新包裹其 `dek` 字段。
  两者必须一起做——只做其一会导致 relock 后组 DEK 无法被 `VaultUnlocker.discoverRootDeks` 登记，
  其下子树落入"不可解锁"。子孙（子组/条目/字段）加密 DEK 基于本组 DEK，移动前后不变，无需重加密。
- **移动条目**：条目块与全部字段块都用"所属组 DEK"加密，移动后 DEK 变了，须把条目块 + 所有字段块
  重加密到新组 DEK 之下（字段 `parent` 仍指向条目，不变）。
- **环检测**：新父不能是自身或子孙（沿 `parentUuidOf` 父链向上遇到 moved 即冲突）。

UI 层：`groupTree` 与 `entryList` 均 `setDragEnabled(true)` + `DropMode.ON` + 各自 `TransferHandler`
（inner class `TreeDragHandler` / `ListDragHandler`），以 `stringFlavor` 携带被拖 uuid；放置目标解析为
文件夹 UUID（左树落到"密码库"区段=顶层 null；中间栏落到条目则取其所属组），调用 `sanctum.move` 后
`refreshAll()`。垃圾桶对象不参与拖拽。

## 验证

core 新增测试：`moveGroupReparentAndSurvivesRelockAfterOldParentGone`（旧父删除并 GC 后 relock 仍可读、
不判不可解锁）、`moveEntryReparentAndSurvivesRelock`、`moveRejectsMovingIntoOwnSubtree`（环检测）。
