# decision20260901-03-sanctum

**决策**：统一条目字段写入入口为 `EntryNode.writeField(name, value, kind)`，消除多套重复的字段写入逻辑。

**模块**：flora-sanctum-core / flora-sanctum-app

**日期**：2026-09-01

## 背景

字段写入逻辑曾分散在多个入口：`ObjectTree.writePreset`（创建期写预设字段）、
`EntryNode.writePreset`（更新期写预设字段，逻辑与前者几乎重复）、`EntryNode.createField`
（自定义字段）、`EntryNode.setNotes`（notes 便捷方法）、`FieldNode.updateValue/updateKind`
（更新已存在字段）。预设与自定义字段在存储层无本质区别（都是条目直接子节点字段块，
`type` 标签区分），入口分散是生命周期与历史追加所致，而非必要。

## 决策

- 新增统一入口 **`EntryNode.writeField(String name, String value, String kind)`**：
  - 预设字段（`PRESET_NAMES`）→ `PREDEF_FIELD`：按字段名复用同名块、空值删除块；
  - 自定义字段 → `CUSTOM_FIELD`：每次新建（随机 uuid），`kind` 可选；
  - 字段块写入时刷新自身 `updateTime`。
- 删除 `EntryNode.createField`、`EntryNode.writePreset`、`ObjectTree.writePreset`。
- `ObjectTree.createEntry` 构造 `EntryNode` 后统一调 `writeField` 写初始预设字段。
- `setNotes` 保留为便捷方法，内部委托 `writeField("notes", …)`。
- 所有 `createField` 调用点（GUI、KDBX/JSON/CSV 导入器、测试）迁移到 `writeField`。
- `FieldNode.updateValue/updateKind` 保留（操作已存在节点，非「写入口」）。

## 效果

- 字段写入收敛为单一入口，预设/自定义差异内聚到 `isPreset` 分支；
- 消除两套 `writePreset` 的重复逻辑；
- 附带行为变化：导入时若字段名恰好是预设名（如 `labels`），之前被当作非法自定义字段
  丢弃（warning），现在直接写入对应预设字段（更合理）。

## 兼容性

纯代码重构，块格式与存储结构不变，无格式兼容问题。
