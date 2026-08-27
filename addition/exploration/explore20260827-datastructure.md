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

## 三、存储结构：三处不一致（重点）

### 1. 预设字段"独立块 + 旧内联字段"双轨回退（最该收敛）

- `EntryNode.presetValue`（EntryNode.java:77）先读独立 `field` 块，缺失则回退 entry 旧 JSON 字段；`updateBuiltins` 写独立块并清理旧字段。
- 已明确"不兼容旧库"，回退分支是死代码，应删除，`presetValue` 直接读独立块即可。
- `createTime` / `updateTime` 是条目级元数据，却和 `password` 等业务字段一样存成 `type=field` 子块（ObjectTree.java:113）。语义上是"条目属性"而非"用户字段"，可考虑作为 entry 块自带系统字段；若坚持"条目一切属性皆子块"则需在文档明确。

### 2. `field` vs `customField` 双类型

- 结构完全相同（name/value/kind/parent），仅靠 name 是否在预设集合区分（ObjectTree.belongsTo:24-27、find:40-44 多一个 switch 分支）。
- 可接受（按 type 直接过滤自定义字段对 GC/遍历更方便），但需在 `StoredNodeType` 注释补"为何不合并"。

### 3. "icon / sshKey 专属 root"注释与实现严重不符（重要）

- IconTree/SshKeyTree/RemoteTree/IconNode/SshKeyNode 注释声称"用 icon/sshKey 专属 root DEK 加密，parent 指向 icon/sshKey root group"。
- 但 `RootTag` 枚举只有 `DATA`（RootTag.java:11），所有写入实际走 `dekForRole(RootTag.DATA)`、parent 指向 DATA 根对象 uuid。
- `RootTag` / `rootDeksByTag` / `dekForRole(RootTag)` 这套参数化多 root 抽象目前是单根的过度设计，且注释与实现不符。
- 建议：A) 真要分 root 就落地 ICON/SSH_KEY 并让 createIcon/createSshKey 用对应 root（增加解锁时多 root DEK 派生）；B) 若不需要，删掉 RootTag 扩展预留、`dekForRole` 退化为 `dataDek()`、修正所有"icon/sshKey root"注释。倾向 B（当前只注册 DATA）。

## 四、文档/实现不符清单（需修正）

| 位置 | 文档说 | 实际代码 |
|---|---|---|
| EntryNode 类注释 + presetValue 注释 | 预设字段"确定性 uuid" | `UUID.randomUUID()`（ObjectTree.java:132、EntryNode.java:247） |
| IconTree/SshKeyTree 等注释 | "icon/sshKey root DEK / root group" | 实际全是 DATA root |

## 五、小残留

- `iconId` 清理残留：`EntryNode.setIcon`（EntryNode.java:159）仍 `remove("iconId")`，是不兼容旧库决策后可彻底删除的迁移代码。
- `FieldNode.kind` 自由字符串 + `FieldKind` enum 仅作已知规范：好的兼容取舍，保留。

## 六、结论

设计整体自然简洁、骨架统一。最该收敛三点：
1. 删除预设字段旧内联回退（不兼容旧库）；
2. 修正"确定性 uuid"与"icon/sshKey 专属 root"两处文档/实现不符；
3. 决定 RootTag 多 root 抽象去留（倾向删减为单根）。

field/customField 双类型与 ref 字段命名可保留，仅补注释。
