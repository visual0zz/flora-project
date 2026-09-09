# 决策：节点排序键 order 改为 base62 字符串小数索引

日期：2026-09-09
模块：flora-sanctum（core：model）

## 背景

`order` 此前是 `long` 型小数索引：`D=2^33`、`L=2`、`X=32`，插入取 `before + (after-before)/2`，
间隙耗尽（`< L`）或追加将溢出时触发 `reassignOrders` 整段重排为 `(i+1)*D`。

该方案有两个固有代价：

1. **精度有界**，必须保留重排路径；重排是 O(n) 的多块改写。
2. **重排路径存在 off-by-one**：`NodeMover.computeOrder` 与 `TreeContext.computeRootSiblingOrder`
   先 `sibs.remove(self)` 再取 `idx`，而 `reassignOrders` 用的是含 self 的完整子列表。
   同父重排且 self 排在 beforeUuid 之前时，`idx` 比重排后的真实索引小 1，元素会被放到错误的邻居旁。
   现有 `OrderingTest` 只断言单调性，覆盖不到。

## 决策

排序键改为 **base62 字符串小数索引**（fractional indexing），形如 `a1`：
第 0 位是整数段，其余是小数段；字符集 `0-9A-Za-z`，字符先后即数值大小且与 ASCII 码点序一致，
故排序直接 `String.compareTo`，字典序即数值序。

按用户要求**不做前向兼容迁移**：旧数值 order 在扫描时被识别为非字符串、按当前顺序重新赋序。

## 关键设计点

- **精度无界，删除重排**：取不到中点时向下追加一位继续取，长度按需增长。
  `reassignOrders` / `collapsed` / `appendOverflow` / `initialOrder` 全部移除，
  `appendOrder` 简化为 `after(maxOrderUnder(parent))`。off-by-one 随重排路径一并消失。
- **追加走小数段进位**（`after`）：末位进一，进位时末位复位为 `1`；小数段全满则整数段进一、
  小数段归零且末位置 `1`；整数段到顶则原样扩展一位。长度约 62 次追加才增一位。
- **约定：键不以 `0` 结尾**。以 `0` 结尾的键与其自身前缀之间不存在可插入区间
  （`a0` 与 `a00` 之间无解）。所有生成路径均避开，保证任意两键之间恒有插入空间。
  `after` 中凡发生进位的分支都把末位复位为 `1`，正是为此。
- **中点算法**：`midpoint` 先剥最长公共前缀再递归，比较始终发生在首个不同的位上；
  该位相邻时固定 a 的整体并追加中点字符（a 的首位已严格小于 b 的首位，追加任何字符仍落在区间内）。
- **确定性生成**：同输入必同输出，便于测试。若将来引入多端并发合并，只需把相邻时追加的固定
  中点字符换成随机字符即可获得并发区分能力。
- **缺失/非法 order 的识别**：`isStringOrder` 用 `JsonValue.isString()` 判断，避免旧数值 order
  让 `getString` 抛异常；非字符串一律按扫描顺序重赋。

## 影响

- `FractionalIndex`：整体重写（67 行 → 139 行）。
- `TreeContext`：`orderOf/maxOrderUnder/appendOrder/computeRootSiblingOrder` 返回 `String`；
  排序比较器由 `Long.compare` 改为 `compareTo`；新增私有 `isStringOrder`；删除 `reassignOrders`。
- `NodeMover`、`ObjectTree`、`RemoteTree`、`SshKeyTree`：随返回类型调整，注释同步。
- app 层**无需改动**：只经 `Sanctum.moveTo` 与 `reorder(UUID, UUID)` 调用，签名未变。
- 测试：`FractionalIndexTest` 重写（11 项，含 3000 次递增/500 次插入的全序不变量）；
  `OrderingTest` 新增 `reorderWithinSameParentKeepsExactPosition`（断言精确位置，正是此前漏掉
  off-by-one 的场景）与 `repeatedHeadInsertKeepsExactOrder`（120 次头部插入，断言精确列表）。
- 验证：core 125 项、app 47 项全绿。

## 踩到的坑

`after` 首版在「整数段到顶」分支先按进位链把小数段清零、再整体扩展，得到 `zz → z01`，
反而小于原值。已改为该分支直接用原串扩展（`zz → zz1`），并补 `afterGrowsWhenHeadCannotCarry` 覆盖。
