# 决策：sanctum 节点排序采用小数索引（fractional indexing）

## 背景
`sanctum` 的两棵树（左分组树、中间条目列表）原先没有排序键：展示顺序直接复用
`TreeContext.childrenByParent`（`ArrayList<UUID>`）的**插入序**。这带来两个问题：

1. `indexObject` 在每次 `write` 时会对被写节点 `remove`+`add` 到末尾，因此**任何写操作**
   （包括改图标）都会把节点推到父列表末尾，刷新后表现为「顺序莫名变化」。
2. 没有任何「用户可改的自然顺序」：拖拽只能改变父分组（reparent），无法组内排序。

## 决策
引入**小数索引**：每个节点块新增可选字段 `order`（`long` 整数），展示顺序改为**按 order 升序**。
参数取 `X=32`、`D=2^33=8{,}589{,}934{,}592`、`L=2`（即 `D/L=2^32`，单列表容量约 `2^63/D = 2^30≈10^9`）。

> 排序键最初采用 `double`，后改为 `long`（见下「为何 long 而非 double」）。

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
- **存储**：`order` 直接存 `long` 整数值。字段可选，旧库缺失时在 `scanAll` 按当前顺序赋
  `(i+1)*D`（仅内存，惰性落盘）。旧版曾短暂使用 `orderBits`（`double` 位模式），语义不同且量级
  巨大，扫描时检测到缺失 `order` 即丢弃旧字段并重新赋序——旧库旧展示顺序本就是扫描顺序，
  故按扫描顺序赋序恰好延续原次序，无需迁移脚本、无格式破坏。
- **排序注入点**：`TreeContext.childrenOf`（组内）与 `DataTree.roots`（顶层，改走
  `childrenOf(根对象 uuid)`）。GC / TrashClassifier / MasterKeyRotator 依赖 parent 边而非顺序，不受影响。
- **重排触发**：两条路径——① 间隙耗尽 `collapsed(before, after)`（即 `after - before < L`）；
  ② 追加将溢出 `appendOverflow(last)`（即 `last > Long.MAX_VALUE - D`）。均整段重排并赋 `(i+1)*D`；
  日常插入只改被移动节点一块。
- **头部插入**：以 0 作虚拟下界，`between(0, first) = first/2`。0 不会被真实节点占用
  （重排与缺省赋序都从 D 起，真实 order 恒 `>= D`），且 `collapsed` 在间隙 `< L` 时先重排，
  故中点永不退化为 0 或任一邻居。头部连续插入 32 次后间隙缩到 `L`（仍可再插一次），第 33 次触发重排。

## 为何 long 而非 double（含溢出要点）
- **容量**：`long` 分辨率恒为 1、不随量级退化；`double` 的 `ulp(x)` 随 x 增长，容量被压到
  `N = 2^(52-X)`。改用 `long` 后约束变为 `N ≈ 2^(63-X)`，**同 X=32 下容量从 ~10^6 提升到 ~10^9（约 1000 倍）**。
- **精确度与简洁**：`long` 往返 JSON 天然精确、无浮点边界情况，可去掉
  `doubleToLongBits`/`longBitsToDouble` 包装层。
- **中点必须防溢出**：`between` 用 `before + (after - before) / 2`，而非 `(before + after) / 2`——
  后者在两者都接近 `Long.MAX_VALUE` 时 `before + after` 会溢出成负数。
- **追加必须防溢出**：`appendOverflow(last) = last > Long.MAX_VALUE - D`，命中则先整段重排
  （重排后量级骤降）再取 `max + D`。
- **L 保持 2 而非数学上最小的 1**：保证 `(b-a)/2 >= 1`，新 order 严格落在两邻居之间；
  间隙为 1 时中点会退化成 `a`，造成顺序冲突。
- **未采用「允许负 order 以让头部插入无限扩展」**：收益是省掉每 32 次一次的重排并把总容量翻倍，
  代价是新增一处下溢守卫，且**混合符号下 `after - before` 本身会溢出**导致中点算错
  （需改用 `(a & b) + ((a ^ b) >>> 1)` 等位运算）。业界主流（Figma/Rocicorp 的
  `fractional-indexing`、Jira 的 LexoRank）也都是「有硬下界 + 用尽即 rebalance」而非无限负向扩展。
  密码管理器单组条目量级下重排成本可忽略，故选更简单的有下界方案。
- **`indexObject` 未改动**：它仍把节点挪到 `childrenByParent` 末尾，但展示已按 `order` 排序，
  故不再影响显示——保留原语义以降低回归风险。
- **条目落到密码库根（ROOT 父）予以允许**：顶层条目本就由 `createEntry(null, …)` 产生
  （parent 即根对象，加密走 `rootDek`），只有 `moveEntry` 原先禁止；为支持顶层条目重排而放开。

## 交互（v1 最小范围）
- 中间列表：拖到某**条目**上 = 插到它之前（跨组则同时 reparent）；拖到**组**/空白 = 进该组末尾。
- 左树拖拽维持原 reparent 语义（落入组=进该组末尾；落入「密码库」区段=顶层），
  **暂不做**左树内 relative 排序。

## 影响
提交 `cf363bb9`（`double` 版）与随后的 `long` 迁移提交。新增 `FractionalIndex`、`OrderingTest`、
`FractionalIndexTest`、`SanctumGuiReorderTest`；改动 `TreeContext`、`DataTree`、`ObjectTree`、
`NodeMover`、`Sanctum`、`SanctumGui`。core/app 全量测试通过（core 98、app 20）。
