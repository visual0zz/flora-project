# 决策 2026-08-04：schemes 抽象结构与落地策略

## 决策 1：SchemeProvider 的分发裁决独立实现

`SchemeProvider` 采用与 `CryptoProvider` 相同的「算法名 → 多实现条目 → 优先级 → 具体度」裁决
语义，但**独立复制实现，不抽取公共泛型 `Registry<T>`**，亦不改动 `CryptoProvider` 现有注册机制。

- **Why:** 用户要求不擅动 `com.flora.crypto.schemes` 与 core 注册/分发机制；`CryptoProvider`
  的裁决逻辑属于 crypto 核心，抽公共基类属于核心重构，需另行评估，不在此方案的变更范围内。
- **How to apply:** 后续实现 `SchemeProvider` 时直接内联裁决逻辑（或极薄的工具方法），
  不得为复用而重构 `CryptoProvider`。

## 决策 2：现有 JSch KEX 与新建 schemes 并行新增、最后迁移

新建 `com.flora.crypto.schemes.engine.kex.*` 作为 `KeyExchange` 协议的独立新实现
（如 `SshDhGroup14Sha256`），与现有 `com.flora.comm.ssh.DHGN` 一族**并行存在**；
待新实现以 RFC 测试向量验证通过后，再切换 `Session` 引用、废弃/迁移 JSch 老实现。

- **Why:** 用户既定原则——"复杂/难验证的密码算法留到最后，且配 RFC 测试向量验证"；
  `DHGN` 一族属于已吸收但需验证的协议代码，不宜在抽象阶段直接重构。
- **How to apply:** 方案落地分两阶段——(a) 先建 schemes 抽象骨架 + 新 KeyExchange 实现；
  (b) 迁移 `Session` 的 KEX 引用与废弃 JSch 老实现的动作，留到验证阶段单独进行。

## 关联

- 方案文档：`addition/design/idea20260804-schemes-abstraction.md`
- 约束来源：`memory/feedback_crypto_boundaries.md`（不动 schemes 工厂、零依赖、难验证算法留最后）
