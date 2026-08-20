# explore20260816-BouncyCastle去除可行性分析

> 背景：flora-sanctum 当前对 Bouncy Castle 依赖仅 `bcprov-jdk18on`，用于三处（Argon2id / AES-GCM-SIV / TOTP HMAC-SHA1）。分析逐步或彻底移除 BC 的可行方案与安全影响。

## 0. 现状

| 功能 | 使用类 | BC 组件 | JDK 是否有替代 |
|---|---|---|---|
| 主密码 KDF | Argon2id | Argon2BytesGenerator | ❌ 无 Argon2 |
| 对象加密 | AES-256-GCM-SIV | GCMSIVBlockCipher | ⚠️ 有 AES-GCM（无 SIV） |
| TOTP | HMAC-SHA1 | HMac+SHA1Digest | ✅ 有 HmacSHA1 |

## 1. TOTP HMAC-SHA1 → JDK JCA（零成本，立即做）

- JDK `Mac.getInstance("HmacSHA1")` 原生支持，RFC 6238 标准即 HMAC-SHA1。
- 当前用 BC `HMac`+`SHA1Digest` 属于**冗余**——同一算法 JDK 直接提供。
- 改动：`Totp.hmacSha1` 换 `javax.crypto.Mac`，输出与 KAT 向量一致（现有 `TotpTest` 可验证）。
- 风险：无。**推荐立即替换**（消除 1/3 的 BC 使用面，零安全变化）。

## 2. AES-256-GCM-SIV → 选项

### 选项 A：换 JDK AES-GCM（`AES/GCM/NoPadding`）
- 实现：`Cipher.getInstance("AES/GCM/NoPadding")` + `GCMParameterSpec`（tag 128-bit），AAD/信封结构不变。
- **安全差异（关键）**：
  - GCM 是 AEAD，但 **nonce 误用灾难性**（复用泄露密钥流）；
  - GCM-SIV 抗误用（RFC 8452：nonce 误用仅泄露"同明文同密文"相等性）。
  - 当前设计每块随机 96-bit nonce（SecureRandomSource 混合熵）。96-bit 随机 nonce 在 GCM 下碰撞概率：约 2⁴⁸ 块后 50%（Birthday）。密码库块数远低于此，**随机源正常时碰撞概率可忽略**。
- 前提：依赖强随机源（当前 SecureRandomSource 基于系统 CSPRNG，满足）；随机源退化/被攻破时 GCM 更脆弱（SIV 的本意是兜底此场景）。
- 实现成本：低（CipherCodec 换 JCA 调用）。

### 选项 B：保留 BC 的 GCM-SIV（推荐于安全优先）
- 零改动，保留抗误用特性。若去 BC 的动机是"减少依赖而非安全"，此路径更稳。

### 选项 C：纯 Java 自实现 GCM-SIV
- GCM-SIV 构造复杂（POLYVAL + AES-CTR），自实现高风险且难审计；社区纯 Java 库稀缺。**不推荐**。

## 3. Argon2id → 选项（去 BC 的最大代价点）

### 选项 A：PBKDF2-HMAC-SHA256（JDK `SecretKeyFactory`）
- 零依赖、JDK 内置；迭代次数需拉高（如 600k~1M 次 SHA-256 匹配当前 Argon2 的 CPU 成本）。
- **安全降级**：PBKDF2 无内存硬性，**可并行（GPU/ASIC）**，弱于 Argon2id 的 256 MiB 内存约束。
- 对主密码 KDF，PBKDF2 仍可接受（历史上 OWASP 曾推荐），但明显弱于 Argon2。

### 选项 B：scrypt（纯 Java 第三方库）
- 有内存硬性（弱于 Argon2 但优于 PBKDF2），需引入纯 Java 依赖（如 lambdaworks/scrypt）。
- 仍是加依赖（非 BC 而已），且库质量/维护需评估。

### 选项 C：JNA 调 libargon2
- 引入平台原生库，违背"纯 Java / jlink 打包"目标。**不推荐**。

### 选项 D：自实现 Argon2（纯 Java）
- Argon2 算法复杂（BLAKE2b + 内存矩阵），自实现性能与正确性风险高。**不推荐**。

## 4. 综合路径建议

| 路径 | 动作 | 安全影响 | 成本 |
|---|---|---|---|
| **A. 最小（推荐）** | 仅 TOTP 换 JDK；保留 BC 的 Argon2id + GCM-SIV | 无 | 低（1 处） |
| **B. 去 BC 但保内存硬** | TOTP→JDK；GCM-SIV→JDK AES-GCM；Argon2→scrypt（第三方纯 Java） | GCM 丢失抗误用（依赖随机源）；Argon2→scrypt 略降 | 中 |
| **C. 彻底去 BC（零三方加密）** | TOTP→JDK；GCM-SIV→JDK AES-GCM；Argon2→PBKDF2 | GCM 丢失抗误用 + KDF 丢失内存硬 | 中（安全代价大） |

**评估**：若动机是"减依赖/审计面"，**路径 A**（TOTP 换 JDK + 保留 BC 两个核心算法）是最优平衡——Argon2 与 GCM-SIV 恰是 BC 在 JDK 上不可替代的两个安全特性，强行替换都需付出安全或复杂度代价。
若动机是"零第三方加密依赖"，路径 C 可行但 KDF 强度（主密码对抗 GPU/ASIC）与 AEAD 抗误用同时降级，需在设计文档 02 中明确风险并相应调参（如 PBKDF2 迭代数）。

## 5. 结论

- **立即做**：TOTP → JDK HmacSHA1（零成本，去一处 BC）。
- **审慎决策**：GCM-SIV / Argon2id 是否保留 BC——两者都是 BC 相对 JDK 的**实质增益**（抗误用 AEAD + 内存硬 KDF），去 BC 属安全降级或复杂度/依赖交换。
- 若执行路径 C，建议同步：设计 02 更新（nonce 策略、KDF 参数）、重新做 KAT/性能验证。

## 6. 实现结果（2026-08-20）

实际执行了"彻底去 BC"但**保留两算法强度**的路线——不降级为 JDK GCM/PBKDF2，而是自研实现两个算法（零第三方加密依赖）：

| 项 | 实现 | 验证 |
|---|---|---|
| TOTP | JDK `Mac HmacSHA1`（RFC 6238） | `TotpTest` + RFC 向量 |
| Argon2id | 复活 flora-root 历史自研实现（`Blake2bDigest` + `Argon2`，RFC 9106，剥离 register/factory 框架依赖为纯静态类） | 与 BC `Argon2BytesGenerator` 固定输入 KAT **逐字节一致** |
| AES-256-GCM-SIV | 集成实现 `GcmSiv`（RFC 8452，单类）：密钥派生（counter 块取前 8 字节）、POLYVAL（GF(2^128) 小端字序，`dot(a,b)=a·b·x^-128`）、tag（POLYVAL⊕nonce 前 12 字节 + 清最高位 + AES）、CTR（初始计数器 = tag 且置最高位，递增低 4 字节）；底层 AES 块引擎复用 JDK `AES/ECB/NoPadding` | 与 BC `GCMSIVBlockCipher` KAT + RFC 8452 官方向量 **逐字节一致** |

要点与坑：
- 密钥派生与 POLYVAL 都依赖**小端字序**域表示；POLYVAL 与 GHASH 的约化多项式/位序不同，不能直接复用 GHASH 乘法（曾误用 `0xE1`/大端导致全部中间值错误）。
- `dot` 的 `x^-128` 因子经 EEA 求逆得到固定常量 `x^-128 = 0100…0492`（小端），预先乘入 H 后用普通域乘法迭代。
- `CipherCodec` 认证失败保持抛 `IllegalStateException`（`BlockResolver`/`ExternalKeyService` 依赖该契约做 key 试解）。
- BC 依赖（`bcprov-jdk18on`）已从 pom 与 module-info 移除；flora-root（codec/JSON）依赖保留。
- 测试：`GcmSivTest`（BC KAT + RFC 官方向量 + 往返/篡改）、`Argon2KdfTest`（BC KAT）、core 53 用例与 app 10 用例全部通过。
