# decision20260901-02-sanctum

**决策**：`createTime` 并入条目 JSON；`updateTime` 改为条目与两种字段（预设/自定义）共有的字段，条目渲染取最大值。

**模块**：flora-sanctum-core（存储格式）

**日期**：2026-09-01

## 背景

原实现中 `createTime`/`updateTime` 是条目下的两个**独立预设字段块**（`PREDEF_FIELD`，随机 uuid，
`parent` 指向条目），每条目从创建起即 3 块起步（条目块 + 两个时间块），是文件数膨胀的来源之一
（约占总块数 1/6）。而时间信息本可随对象块内嵌，无需独立成块。

## 决策

- `createTime` 直接存**条目 JSON**（创建时固定，不再独立块）；
- `updateTime` 成为**条目与所有字段（预设 + 自定义）共有的 JSON 字段**：任何对象块被写入/重写时
  刷新自己的 `updateTime`；
- `EntryNode.updateTime()` 返回 **max(条目自身 updateTime, 全部字段 updateTime)**，反映
  「条目任一部分最后被改」的时间；渲染端（app）已调用该方法，自动生效；
- `EntryFields.PRESET_NAMES` 移除 `createTime`/`updateTime`。

## 效果

- 每条目创建时从「3 块起步」降为「1 块起步」；字段块（预设/自定义）各自内嵌 `updateTime`，
  不再有专门的时间块；
- `updateTime` 语义更精确：字段级时间戳 + 取 max，条目整体更新时间=最后被改的组件时间；
- 逐字段审计粒度保留（每个字段块自己的 `updateTime`）。

## 改动点

- `ObjectTree.createEntry`：条目 JSON 写入 `createTime`/`updateTime`；移除两个时间块写入。
- `ObjectTree.writePreset` / `EntryNode.writePreset` / `EntryNode.createField`：字段块内嵌 `updateTime`。
- `FieldNode.touchAndWrite`：字段更新时刷新自身 `updateTime`。
- `EntryNode.rename` / `setIcon` / `updateBuiltins`：条目级修改时刷新条目 `updateTime`。

## 兼容性

**格式变更**：旧库中 `createTime`/`updateTime` 独立预设块在新代码下不再被识别为预设字段，
会被当作普通字段列出。项目未发布正式数据，接受此不兼容变更。
