# decision20260901-04-sanctum

**决策**：统一预设字段与自定义字段的存储 type 为单一 `FIELD`，消除两种字段 type 的存储区分。

**模块**：flora-sanctum-core（存储格式）

**日期**：2026-09-01

## 背景

字段块曾分 `PREDEF_FIELD("predefField")` 与 `CUSTOM_FIELD("customField")` 两种存储 type，
其块负载结构完全相同（name/value/kind/parent），区别仅在 type 标签。而「预设 vs 自定义」
的语义已被 `EntryFields.PRESET_NAMES`（字段名集合）完整定义，type 标签是冗余的第二个信号，
且两处信号需保持一致（曾因 type 过滤与 name 判定并存而增加维护负担）。

## 决策

- `StoredNodeType` 删除 `PREDEF_FIELD`/`CUSTOM_FIELD`，新增统一 **`FIELD("field")`**；
- `fromTag` 兼容旧值：`predefField`/`customField` → 映射为 `FIELD`（旧库无需迁移即可读）；
- `EntryNode.writeField` 预设/自定义分支统一写 `FIELD.tag()`；
- `fields()`（自定义字段列表）改按「name 不在 `PRESET_NAMES` 且非 externalKey」过滤；
- `presetChild`（预设字段定位）改按「name 命中且非 externalKey」过滤；
- `ObjectTree.belongsTo`、`FieldNode.type()` 默认值、`ExternalKeyService` 字段写入改 `FIELD`；
- `SanctumGui` 移除 `PREDEF_FIELD` 分支（原走到远程面板实为「远程不存在」的空面板）。

## 效果

- 预设/自定义语义收敛到单一事实源 `PRESET_NAMES`；
- 块格式统一为一种字段 type，`StoredNodeType` 枚举减少两个值；
- 读端兼容旧块（`predefField`/`customField` 自动映射），无需迁移。

## 兼容性

**格式变更（写端）**：新块 `type="field"`；**读端兼容**：旧块两种 type 均映射为 `FIELD`，
旧库可直接打开，无需迁移。
