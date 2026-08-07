# 决策 20260807-03：熵测量门面接口整洁化

- 日期：2026-08-07
- 模块：`flora-root` / `com.flora.entropy.mesure`
- 关联审查：`addition/codereview/review20260807-02-entropy-interface.md`

## 决策内容

针对用户反馈“熵测量接口不够整齐”，对 `Entropy` 门面层做以下接口调整（引擎层与注册表层结构不变）：

1. **明确分层**：`Entropy` 定位为“字符串便捷门面”（String→UTF-8 适配 + 常用语义封装）；`EntropyEstimator` 定位为“注册表 + 字节级归一化/聚合/扩展层”。消除两个“门面”的权责重叠。
2. **`normalized` 更名为 `shannonDensity`**：原 `normalized` 未标明所针对的算法（实为 SHANNON 归一化密度），命名易混淆；更名后语义自明。生产唯一调用方 `SecretCheck` 与测试同步更新，**不保留兼容别名**（遵循项目“不留兼容垫片”约定）。
3. **新增 `Entropy.density(String name, String s)`**：按算法名取归一化密度的统一入口，使已注册的 BASE16/64/64URL、ENGLISH 等算法也能通过字符串门面直接访问，修复“门面只暴露 2/4 算法”的缺口。
4. **消除 `/8.0` 魔法数**：在 `ComplexityRatio` 新增 `ratio(byte[])`（未缩放压缩比），`EntropyEstimator` 新增 `compressionRatio(byte[])` 委托，`Entropy.complexityRatio` 改为调用之。`×8` 与 `/8` 收口于同一引擎，门面不再感知内部缩放常数。数值结果与改动前等价。
5. **统一 null/empty 处理**：门面 String 方法均在边界处 `if (s == null || s.isEmpty()) return 0`；`shannon(byte[])` 补上与 javadoc 一致的显式 null/empty 检查。

## 为什么（动因）
- 用户明确感知到接口不整齐，且门面层确实存在“返回值量纲混杂、命名无算法前缀、魔法常数跨层耦合、重复入口”四类可量化问题。
- 引擎层（单一职责、注册表模式）设计良好，无需改动；整洁化应集中在门面层的职责划分与命名。

## 如何应用
- 后续新增熵算法：只需在 `EntropyEstimator` 静态块 `registerMetric`，门面 `density(name, s)` 自动可用，无需再为门面补专属方法。
- 调用方取“归一化密度”一律走 `Entropy.shannonDensity` / `Entropy.density(name, s)` / `EntropyEstimator.density`；取“可压缩度”走 `Entropy.complexityRatio`；勿再手写 `/8.0` 还原。
- 遗留项 `SecretCheck.entropyDensity` 与库 `density` 的归一化口径差异（F7）仍待后续评估，迁移前保持现状。
