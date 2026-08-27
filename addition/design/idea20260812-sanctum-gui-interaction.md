# idea20260812-sanctum-gui-interaction

## 背景

`SanctumGui`（flora-sanctum-app）是 3000+ 行的 Swing God-class，交互逻辑存在两类脆弱性：

1. **刷新靠手写配对调用**：每次改模型后需在约 40 处手动调用
   `rebuildGroupTree()` + `refreshEntryList(currentSearchQuery())`，顺序/漏调会导致
   界面与模型不一致（如只刷树不刷列表、选中错位）。
2. **条目列表用并行数组**：`entryUuids` / `listItemTypes` / `listItemIcons` 三个
   `ArrayList` 与 `DefaultListModel<String>` 按索引对齐，渲染器、双击导航、选中解析
   都依赖"索引在三个数组间对齐"，极易越界或错位。

此外 `SyncService` 在 `doSync` 内每次 `new`，`launch` / `launchDirect` 两条入口引导逻辑重复。

## 已落地的重构（本次）

- **GUI 侧变更总线 `ModelChangeBus`**（`app/ui/ModelChangeBus.java`）：UI 在每次"会改
  数据结构"的操作后调用 `markDirty()`，由 `refreshAll()` 统一触发一次 树+列表+工具栏
  重建。多个连续 `markDirty()` 只重建一次。`SanctumGui` 在解锁后 `modelBus.subscribe(this::refreshAll)`。
- **域对象列表模型**：删除三个并行数组，改为 `DefaultListModel<EntryListItem>`；
  `EntryListItem`（record，含 uuid/type/display/iconRef）直接承载数据，渲染器与
  双击导航从对象读取，消除"索引对齐"错误模式。
- **`SyncService` 注入**：`SanctumGui` 持有 `syncService` 字段（解锁时创建），
  `isFullyManaged()` / `doSync()` 复用，去掉每次 `new SyncService(root)`。
- **统一启动入口**：`launch(Path...)` 取代原 `launch()` / `launchDirect()` 两入口；
  原 `run()` 改名为 `bootstrap()`（HTTP/托盘/自动锁定编排收敛于此）。
- **渲染器外提**：`SettingsTreeRenderer` / `SettingsEntryRenderer` 抽为同包顶层类
  （自包含、无实例依赖）。`FolderTreeRenderer` / `EntryListRenderer` 因强依赖实例
  方法（`groupsById`/ `iconById` 等）保留为内部类。

## 暂未落地（有意为之）

- **将 SanctumGui 拆分为 Unlock/EntryList/Settings/Sync 控制器**：该文件近 3000 行、
  数十个私有 helper 互相引用，整体拆分是高风险改造，边际安全收益在"总线+域对象模型"
  已落地后显著降低。保留为后续独立任务，需配合进一步方法归并后再分模块。

## 验证

- 核心 + 应用模块测试全绿（`mvnw -pl flora-sanctum/... -am test`）。
- 解锁/新建条目/新建文件夹/删除/改名/导入图标/SSH/远程/同步 等路径的刷新
  统一走 `modelBus.refresh()`，行为与重构前一致。
