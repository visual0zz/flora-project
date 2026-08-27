# 数据结构设计审查（2026-08-27）

审查范围：`flora-sanctum-core` 的 `model` 包（TreeNode/DataTree/ObjectTree 及各节点、Ref、StoredNodeType、RootTag、Vault、LibraryConfig）。

## 一、整体骨架：良好且统一（正面）

- 单根 + 类型分树 + 统一 `TreeNode` / `DataTree` 抽象基类；所有节点读写经 `TreeContext`。
- `StoredNodeType`（持久化）与 `ViewNodeType`（展示）解耦，每个存储类型声明自己的展示归属。
- `Ref` 统一引用（`node:` / `builtin:` scheme）把 icon / keyRef 收口到同一结构，正面设计，应保留。

## 二、字段命名：整体统一，少量异味

一致：`type`、`parent`（父 uuid 字符串）、`name`、`value`、`kind`、`dek`（Base64 包裹 DEK）、`data`（Base64 图标）、`format`、`icon`/`keyRef`（Ref 对象）。

异味：
1. SSH 私钥字段叫 `privateKey`，而其它值统一叫 `value`（`FieldNode`）。语义不同可解释。
2. config 的"key"藏在 `name` 字段：`LibraryConfig.getConfig(key)` 实际按 `name==key` 查找，key/value 概念被 name/value 复用，略含糊。
3. ref 字段命名不统一：`icon`（纯名词）vs `keyRef`（role+Ref 后缀），同为 Ref，风格漂移。

## 三、存储结构：三处不一致（已于 2026-08-27 收敛）

### 1. 预设字段"独立块 + 旧内联字段"双轨回退 —— 已收敛为"仅删旧内联回退"

- 删除 `EntryNode.presetValue` 的 entry 旧字段回退分支，`updateBuiltins` 删除旧字段清理循环；`createTime`/`updateTime` 仍作为独立 `type=field` 子块（保持"条目一切属性皆子块"）。
- 同时删除 `EntryNode.setIcon` 中仍清理旧 `iconId` 的迁移残留。

### 2. `field` vs `customField` 双类型 —— 已确认保留双类型，逻辑往 type 靠拢

- 在 `StoredNodeType` 注释补"为何不合并"（按 type 直接过滤对 GC/遍历更方便，无需依赖 name 是否在预设集）。
- `EntryNode.presetChild` / `fields()` 改为按 `StoredNodeType.FIELD` / `CUSTOM_FIELD` 区分，不再用 `EntryFields.isPreset(name)` 判断（仅 `createField` 拒绝预设名时仍用 `isPreset`）。

### 3. "icon / sshKey 专属 root"注释与实现严重不符 —— 已收敛为单根

- 删除了 `RootTag` 枚举（实际只有 `DATA` 一个值）。
- `Vault` 从参数化 `Map<RootTag,...>` + `dekForRole(RootTag)` / `rootObjectUuid(RootTag)` 退化为单根：
  `addRootDek(byte[])` / `addRootObjectUuid(UUID)` / `rootObjectUuid()` / `dataDek()`。
- 所有调用点（LibraryConfig/RemoteTree/RemoteNode/SshKeyTree/IconTree/ObjectTree/DataTree/TreeContext/ExternalKeyService/MasterKeyRotator/Sanctum/VaultCreator/VaultUnlocker）改为新签名。
- 修正 IconTree/IconNode/SshKeyTree/SshKeyNode/RemoteTree/RemoteNode/ObjectTree/VaultCreator 中"icon/sshKey root DEK / root group"为"唯一根（data）DEK"。

## 四、文档/实现不符清单（已修正）

| 位置 | 文档说 | 实际代码 | 状态 |
|---|---|---|---|
| EntryNode 类注释 + presetValue 注释 | 预设字段"确定性 uuid" | `UUID.randomUUID()` | 注释已改为"随机 uuid" |
| IconTree/SshKeyTree 等注释 | "icon/sshKey root DEK / root group" | 实际全是 DATA root | RootTag 已删，注释已修正 |

## 五、小残留（已清理）

- `iconId` 清理残留：`EntryNode.setIcon` 中 `remove("iconId")` 已删除（不兼容旧库决策后无需迁移）。
- `FieldNode.kind` 自由字符串 + `FieldKind` enum 仅作已知规范：好的兼容取舍，保留。

## 六、结论

设计整体自然简洁、骨架统一。三处存储结构不一致已全部收敛：
1. 删除预设字段旧内联回退（不兼容旧库）；
2. 修正"确定性 uuid"文档与"icon/sshKey 专属 root"注释/实现不符；
3. RootTag 多 root 抽象删减为单根（dataDek/rootObjectUuid）。

field/customField 双类型保留并补注释，ref 字段命名沿用统一结构。
