# 熵测量接口整洁度审查（com.flora.entropy.mesure）

- 日期：2026-08-07
- 模块：`flora-root` / `com.flora.entropy.mesure`
- 触发：用户反馈“熵测量的部分代码，接口有些不够整齐”

## 总体判断

熵度量的**引擎层**（`engine/*`、`EntropyMetric`）设计是整洁的：算法只输出“每字节熵总量”，上限与密度归一化统一由 `EntropyEstimator` 推导，单一职责清晰。不整齐集中在**门面层 `Entropy`** 与 `EntropyEstimator` 的接口分工、返回值语义、隐式魔法数、null 处理四处。

以下按严重度列出问题，并标注本轮已修复与否。

## 具体问题

### F1【已修复】两个门面类职责重叠，`minDensity` 在两处重复暴露
`Entropy`（门面）与 `EntropyEstimator`（注册表+归一化）都是 static 工具类，都提供 `minDensity(String, String...)` 与 `minDensity(byte[], String...)`，签名完全一致；`Entropy.minDensity` 仅是 `EntropyEstimator.minDensity` 的薄转发。两个类都自称“门面”，调用方不清楚该调哪个。

**修复**：在两类的类注释中明确分层——`Entropy` 是“字符串便捷门面”（负责 String→UTF-8 适配与常用语义封装），`EntropyEstimator` 是“注册表+字节级归一化/聚合/扩展层”。保留 `Entropy.minDensity` 作为字符串便捷入口，但注释指明字节级请用 `EntropyEstimator.minDensity`。

### F2【已修复】门面只暴露 4 个算法中的 2 个，名不副实
`Entropy` 的 javadoc 大谈 4 类度量，但门面方法只有 `shannon`/`normalized`(=SHANNON)、`complexityRatio`(=COMPLEXITY_RATIO)、`minDensity`。注册了的 `BASE16/64/64URL`、`ENGLISH` 无法通过门面直接取密度，必须绕到 `EntropyEstimator.metric("BASE64")`。

**修复**：新增 `Entropy.density(String name, String s)`，作为按算法名取密度的统一入口，覆盖全部已注册算法（含 BASE/ENGLISH）。

### F3【已修复】返回值量纲不统一 + 命名风格不一致
门面各方法返回语义混杂：

| 方法 | 返回 | 量纲 |
|------|------|------|
| `shannon` | bit/字节 [0,8] | 原始熵幅度 |
| `normalized` | [0,1] | 归一化密度（且未标明是 SHANNON）|
| `complexityRatio` | [0,1] | 压缩比（与熵密度不同概念）|
| `minDensity` | [0,1] | 密度最小值 |

`normalized` 实指“归一化香农熵”却无算法前缀；`complexityRatio` 又带算法名前缀——命名风格不统一。且 `complexityRatio` 返回“可压缩度”而非“熵密度”，与 `density`/`minDensity` 概念不同却被并列，易误用。

**修复**：
- `normalized` 更名为 `shannonDensity`，显式标明针对 SHANNON 算法；同步更新唯一生产调用方 `SecretCheck` 与测试（未保留兼容别名，符合项目“不留兼容垫片”约定）。
- 类注释中明确：`shannon`=原始幅度；`shannonDensity`/`density`=归一化密度；`complexityRatio`=可压缩度（非密度）；`minDensity`=密度聚合。

### F4【已修复】`/8.0` 魔法常数耦合（门面泄漏引擎内部缩放）
`Entropy.complexityRatio` 内部 `EntropyEstimator.metric("COMPLEXITY_RATIO").measure(...) / 8.0`。门面必须知道 COMPLEXITY_RATIO 引擎把“每字节熵近似”乘以 8 的内部约定才能还原比值。若引擎改缩放，门面会静默算错。

**修复**：在 `ComplexityRatio` 新增 `public static double ratio(byte[])`（返回未缩放的压缩比 [0,1]）；`EntropyEstimator` 新增 `compressionRatio(byte[])` 委托之；门面 `complexityRatio` 改为调用 `EntropyEstimator.compressionRatio(...)`。`×8` 与 `/8` 逻辑收口在同一引擎内，门面不再感知缩放常数。数值结果与改动前等价。

### F5【已修复】null/empty 处理风格不一致
`Entropy.shannon(String)` 显式判 null/empty 返回 0；`Entropy.shannon(byte[])` 无显式检查、靠引擎兜底（与其 javadoc“null 按空数组”不符）；`EntropyEstimator.density`/`minDensity` 又各自处理。null 契约散落多处。

**修复**：门面所有 String 方法统一在边界处 `if (s == null || s.isEmpty()) return 0`；`shannon(byte[])` 补上与 javadoc 一致的显式 null/empty 检查。`EntropyEstimator` 仍是字节级 null 的唯一权威处理点。

### F6【遗留·低优先】`registerMetric` 签名样板
`EntropyEstimator.registerMetric(EntropyMetric prototype, Function<String,? extends EntropyMetric> factory)` 中 `prototype` 仅用作读取 `AlgorithmFamily` 元数据（supportedAlgorithms/priority），`factory` 才是造实例的。对 `BaseAlphabetEntropy` 这种“一个原型声明多算法名”的实现，该签名是必要的（factory 按名造对应实例），故保留。建议在 javadoc 中说明 prototype 与 factory 的分工——本轮已在静态块注释保持清晰，暂不重构。

### F7【遗留·需关注】`SecretCheck` 重写了“归一化熵密度”，口径与库不一致
`SecretCheck.entropyDensity(byte[])`（`flora-osmetes/.../SecretCheck.java:428`）按“实际出现的不同字节数 `distinct`”归一化（`h / log2(distinct)`），而库的 `EntropyEstimator.density`（=`entropy / maxPerByte(n)`，`maxPerByte = log2(min(n,256))`）按“数据长度上界”归一化。两者是**不同的归一化口径**，却都叫“归一化熵密度”。

这是跨模块的接口不整齐：库的 `density` 未提供“按 distinct 归一化”的变体，导致调用方不得不本地重写。直接统一会改 SecretCheck 检测行为并可能破坏其 30 个测试，故本轮**保留并仅记录**。建议后续在 `EntropyEstimator` 增加显式支持（如 `density(name, data, Normalization.BY_DISTINCT)`）或文档明确两种口径的适用场景，再迁移 SecretCheck。

### F8【遗留·文档】`pick()` 异常文案混用
`EntropyEstimator.metric(name)` 在 `list==null` 时抛“未注册”，`pick()` 返回 null 时抛“算法重复注册”，两种失败原因共用 `IllegalArgumentException`。属次要整洁点，暂不改动。

## 已实施改动文件
- `flora-root/.../engine/ComplexityRatio.java`：新增 `ratio(byte[])`。
- `flora-root/.../EntropyEstimator.java`：新增 `compressionRatio(byte[])`。
- `flora-root/.../Entropy.java`：重写门面，明确分层、更名 `normalized`→`shannonDensity`、新增 `density(name, s)`、消除 `/8.0`、统一 null 处理。
- `flora-osmetes/.../check/SecretCheck.java:383`：`Entropy.normalized` → `Entropy.shannonDensity`。
- `flora-root/.../test/.../EntropyTest.java`：跟随更名，新增 `density(name, s)` 统一入口测试。

## 验证
`flora-root` 的 `EntropyTest` 与 `flora-osmetes` 的 `SecretCheckTest` 均通过。

## 遗留建议
1. 评估 F7：是否需在 `EntropyEstimator` 暴露“按 distinct 归一化”变体并迁移 SecretCheck，消除库内外重复实现。
2. 评估 F6/F8 的文档与异常清晰度微调（低风险，可暂缓）。
