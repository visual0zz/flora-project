# 决策：小数索引上移至 flora-root，并改用 Rocicorp 完整算法（变长整数）

日期：2026-09-09
模块：flora-root（新增 `com.flora.root.collect.order`）、flora-sanctum-core（改用）

## 背景

`decision20260909-01/02` 在 sanctum 内实现了一版 base62 小数索引。它把第 0 位当作普通的一位，
于是**头部插入只能靠反复取中点去逼近下界**——每次把可用间隙砍半，约 6 次就耗尽、必须扩展一位。

实测后果：连续前插 3000 次后键长约 **500 字符**（每 ~6 次 +1）。
而 Rocicorp 的实现（`fractional-indexing`，源自 David Greenspan 的文章）在同样操作下仍是 3 字符。

差距根源不是调优，而是**结构设计**：Rocicorp 把第 0 位当作独立的整数段头部，
前插/追加消耗的是"整数部分减一/加一"，根本不动小数部分。

## 决策

1. 在 flora-root 新建 `com.flora.root.collect.order.FractionalIndex`，**完整实现 Rocicorp 算法**
   （含变长整数编码），作为通用库沉淀。
2. sanctum 删除本地副本，改为依赖 flora-root 的实现。

## 算法要点

### 键的结构
```
整数部分（head + 若干数字位）  +  小数部分（数字位）
        'a' 'Z' …                    '0' 'V' …
```
- 数字位取自 `DIGITS`（62 字符，`0-9A-Za-z`）；
- 头部取自 `INT_DIGITS`（52 字符，`A-Za-z`），**编码整数部分的长度与量级**：

```
integerLength(head) = i < 26 ? 26 - i + 1 : i - 26 + 2
```

  即 `Z`/`a` 最短（2 位），向两端递增到 `A`/`z`（27 位）——长度随量级增长，这就是"变长整数"。

### 前插与追加为何便宜
- 追加 = 整数部分加一（`incrementInteger`），前插 = 整数部分减一（`decrementInteger`）；
- 只有进位/借位传播到头部时才改变键长，且头部本身编码了长度，因此位数自动对齐；
- 每个头部提供 `62^(len-1)` 次插入：`Z`(2 位) 62 次 → `Y`(3 位) 3844 次 → `X`(4 位) 238328 次……

连续前插/追加 3000 次后键长仍是 **3**（对比旧版约 500）。

### 约束
- 小数部分不能以 `0` 结尾（以 0 结尾的键与其自身前缀之间不存在可插入区间）。
  注意是整个**小数部分**，不是整个键——`a0` 的整数部分就是 `a0`，小数部分为空，合法。
- 键不能等于最小整数 `A` + 26 个 `0`，它已无法再向前生成。

### midpoint
与旧版一致：先剥最长公共前缀再递归；相邻（取不到中点）时，若上界有多位则取其首位
（它严格落在区间内），否则固定下界首位并对剩余部分继续求中点。

## API 设计

```java
FractionalIndex.between(a, b)          // 确定性，与官方实现字节兼容
FractionalIndex.betweenJittered(a, b)  // 取中点时随机取值，用于并发插入
FractionalIndex.nBetween(a, b, n)      // 一次生成 n 个均匀铺开的键（比逐个生成更短）
FractionalIndex.compare(a, b)          // 字典序比较，null 排最前
FractionalIndex.isValid(key)           // 校验外部来源的键
```

**为什么把 `compare` 放进这个类**：官方文档特别强调必须用字典序、禁用大小写无关比较
（`localeCompare` 会给出错误次序）。把比较器与键格式放在一起，可避免调用方各自写错。

**jitter 的位置**：只在"取中点"分支生效（在开区间内随机取值）。追加/前插走整数部分加减一，
是确定性的——官方核心库同样如此，jitter 由第三方扩展提供。

**随机源的选择**：用 `ThreadLocalRandom` 而非项目的混合熵源（`SecureRandomSource`）。
`decision20260908-01` 的"统一走混合熵入口"针对的是 uuid——唯一性依赖熵质量，源退化会导致
"不重复但可预测"；jitter 只用于区分并发插入，不承载安全语义，且 `FractionalIndex`
是纯静态工具类，引入实例化的随机源会破坏其无状态性质。

## 影响

- flora-root：新增 `com.flora.root.collect`（含 package-info）与
  `com.flora.root.collect.order.FractionalIndex`；`module-info` 新增一条 exports。
  新增测试 11 项，其中 `matchesReferenceExamples` / `nBetweenMatchesReferenceExamples`
  直接对齐官方 README 的示例（`a0`、`a1`、`Zz`、`a1V`、`['a0G','a0V']`…），
  作为跨语言实现兼容性的回归基准。
- sanctum：`TreeContext` / `NodeMover` / `RemoteTree` / `SshKeyTree` 改用新实现；
  删除本地 `FractionalIndex` 及其测试。
- 键格式变化：首个键由 `a1` 变为 `a0`（对齐官方）。按"不做前向兼容"的既定方针，
  旧数值 order 在扫描时按当前顺序重赋。
- 验证：flora-root 全量、sanctum-core 114 项、sanctum-app 47 项全绿。

## 踩到的坑

**`orderOf` 的缺失值被当成了上界。** 初版让 `orderOf` 在缺失时返回 `""`，
而 `maxOrderUnder` 直接取最大值——当父下只有不写 order 的节点（如 Icon）时，
max 为 `""`，`between("", null)` 抛"非法键"。

已改为：`orderOf` 缺失返回 `null`（语义明确），`maxOrderUnder` 跳过 null，
所有排序点统一走 `FractionalIndex.compare`（它把 null 排在最前）。
