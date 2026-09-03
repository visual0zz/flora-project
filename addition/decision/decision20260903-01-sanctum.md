# 决策：sanctum 节点排序采用小数索引（fractional indexing）

## 背景
`sanctum` 的两棵树（左分组树、中间条目列表）原先没有排序键：展示顺序直接复用
`TreeContext.childrenByParent`（`ArrayList<UUID>`）的**插入序**。这带来两个问题：

1. `indexObject` 在每次 `write` 时会对被写节点 `remove`+`add` 到末尾，因此**任何写操作**
   （包括改图标）都会把节点推到父列表末尾，刷新后表现为「顺序莫名变化」。
2. 没有任何「用户可改的自然顺序」：拖拽只能改变父分组（reparent），无法组内排序。

## 决策
引入**小数索引**：每个节点块新增可选字段 `orderBits`（`double` order 的 64 位 bits），
展示顺序改为**按 order 升序**。参数取 `X=32`、`D=2^33=8{,}589{,}934{,}592`、`L=2`
（即 `D/L=2^32`，单列表容量约 `2^20≈10^6`）。

## 备选方案与取舍
- **A. 保留插入序，仅修 `indexObject` 不再把节点挪到末尾**：改动最小（约 1 行），能修掉
  「改图标导致顺序跳动」，但**无法提供组内手动排序能力**，且顺序仍隐式依赖落盘/扫描顺序，
  导入后顺序不可控。
- **B. 小数索引（选定）**：既能顺带修掉图标 bug（`setIcon` 不触碰 `order`，展示改按 `order`
  排序，插入序不再影响显示），又新增组内手动拖拽排序；且重排时**只改写被移动节点一个块**，
  数据修改范围最小。代价是需要新增字段与相对位置语义。
- **C. 显式整数字段 + 每次重排大范围自增**：实现简单，但插入时要改后面所有兄弟的序号，
  与「最小化数据修改范围」的目标相悖。

## 关键设计点
- **存储**：`orderBits` 存 `Double.doubleToLongBits(order)` 的 `long`，而非直接存 `double`，
  规避 JSON 数字格式化对 `double` 精度的任何影响。字段可选，旧库缺失时在 `scanAll` 按当前
  顺序赋 `i*D`（仅内存，惰性落盘），无迁移脚本、无格式破坏。
- **排序注入点**：`TreeContext.childrenOf`（组内）与 `DataTree.roots`（顶层，改走
  `childrenOf(根对象 uuid)`）。GC / TrashClassifier / MasterKeyRotator 依赖 parent 边而非顺序，不受影响。
- **重排触发**：`collapsed(before, after)` 即中点落到端点（`ulp` 耗尽）时整段重排并赋 `(i+1)*D`；
  日常插入只改被移动节点一块。
- **`indexObject` 未改动**：它仍把节点挪到 `childrenByParent` 末尾，但展示已按 `order` 排序，
  故不再影响显示——保留原语义以降低回归风险。
- **条目落到密码库根（ROOT 父）予以允许**：顶层条目本就由 `createEntry(null, …)` 产生
  （parent 即根对象，加密走 `rootDek`），只有 `moveEntry` 原先禁止；为支持顶层条目重排而放开。

## 交互（v1 最小范围）
- 中间列表：拖到某**条目**上 = 插到它之前（跨组则同时 reparent）；拖到**组**/空白 = 进该组末尾。
- 左树拖拽维持原 reparent 语义（落入组=进该组末尾；落入「密码库」区段=顶层），
  **暂不做**左树内 relative 排序。

## 影响
提交 `cf363bb9`。新增 `FractionalIndex`、`OrderingTest`、`FractionalIndexTest`、
`SanctumGuiReorderTest`；改动 `TreeContext`、`DataTree`、`ObjectTree`、`NodeMover`、
`Sanctum`、`SanctumGui`。core/app 全量测试通过（core 96、app 20）。
