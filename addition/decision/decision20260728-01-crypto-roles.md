# decision20260728-01-crypto-roles

- 日期: 2026-07-28
- 作者: CodeBuddy
- 模块: flora-root (com.flora.crypto.core)

## 决策

`flora-root` 的 crypto 抽象层**全面对齐 Bouncy Castle 轻量 API 的接口族，但绝不引入 BC 依赖**。

具体规则：
1. 所有缺失的 BC 角色接口（`ExtendedDigest` / `Xof` / `AsymmetricCipher` / `Wrapper` / `Agreement` /
   `DerivationFunction` / `PBEParametersGenerator` / `AsymmetricCipherKeyPairGenerator` /
   `BlockCipherPadding` / `AEADBlockCipher` / `KEM` / `EntropySource` / `SP80090DRBG` 等）都补上，使接口形状与 BC 一致。
2. **JDK 已具备的能力**用 JDK 适配器实现（`JdkWrapper` / `JdkAgreement` / `JdkPBEParametersGenerator` /
   `JdkAeadBlockCipher` / `JdkAsymmetricKeyPairGenerator`），熵源用 `SecureRandomEntropySource`（包 `SecureRandom`）；
   不依赖任何第三方库。
3. **JDK 概念上装不下的能力**留**最简占位实现**
   （`PlaceholderXof` / `PlaceholderDerivationFunction` / `PlaceholderKem`，对应方法抛 `UnsupportedOperationException`），
   并随附**纯 Java、零依赖**的真实实现作为范例：派生 `Kdf2DerivationFunction` / `HkdfDerivationFunction`、
   KEM `AgreementBasedKem`（经典曲线真实实现）、DRBG `HMacDrbg`（NIST SP800-90A HMAC_DRBG）。
4. 模式/填充按 BC「对象组合」风格以纯 Java 实现（`CBCBlockCipher` / `SICBlockCipher` 等、`PKCS7Padding` 等），
   不依赖 JDK 的变换字符串；GCM 因 GHASH 自实现风险高，内部委托 JDK。

## Why

用户明确要求「全面对齐 BC 的接口」但「不要引入 BC 依赖」。`flora-root` 为零依赖工具库，
引入 BC 会破坏该硬约束；而 BC 的接口形状本身无版权障碍，可等价重写。

## How to apply

- 后续要补算法/角色时，优先按 BC 的角色接口形态新增，而非在现有大接口里堆方法。
- 遇到 JDK 没有概念槽位的能力（如新的 XOF、KEM），先放占位实现 + `registerXxx` 接入点，
  真实引擎（如 SHAKE、ML-KEM）后续以 `CryptoProvider.registerXxx` 覆盖占位即可，不影响调用方。
- `KEM`（经典曲线 `AgreementBasedKem`）、`EntropySource`（`SecureRandomEntropySource`）、
  `SP80090DRBG`（`HMacDrbg`）已于本期实现并接入 `CryptoProvider`，后量子 KEM（ML-KEM）仍走 `PlaceholderKem` 占位。
