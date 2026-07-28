# crypto 包缺失的角色接口清单

- 日期: 2026-07-28
- 作者: CodeBuddy
- 背景: 在对比 `flora-root` 的 `com.flora.crypto`(BC 轻量 API 风格的抽象层)与 JDK JCA 时,
  结论是「JDK 接口在概念上能覆盖 BC 的约 90% 算法族,但有几类 JDK 的接口形状本身装不下,
  且 `flora-root` 还缺一批 BC 式轻量角色」。本文列出 `com.flora.crypto.core` 当前缺失的角色接口,
  用于指导后续补齐,使本项目的抽象在**概念上对齐 BC 的接口族**。

## 一、现状:已有哪些角色

`com.flora.crypto.core` 当前角色接口:

- `Digest` —— 定长摘要
- `BlockCipher` —— 分组密码(块/流统一在 `Cipher` 概念下)
- `StreamCipher` —— 流密码
- `AsymmetricBlockCipher` —— 非对称分组密码(RSA 等)
- `Mac` —— 带密钥 MAC
- `Signer` —— 签名/验签
- `BufferedBlockCipher` —— 缓冲装饰器(非独立角色,包裹 `BlockCipher`)
- `CryptoProvider` —— 按名注册/查询的注册表
- 参数族: `CipherParameters` / `KeyParameter` / `AsymmetricKeyParameter` /
  `ParametersWithIV` / `ParametersWithRandom`
- 引擎: `JdkKeyPairGenerator`(仅包装 JCA 的 `KeyPairGenerator`)

## 二、缺失角色清单

缺口分四类:A = JDK 也装不下的真概念缺口;B = JDK 有槽位但缺 BC 式轻量角色;
C = BC 用可组合对象表达、JDK/flora-root 用字符串折叠的部分;D = 支撑型(多属 B 子类)。

### A 类 —— 真正的概念缺口(JDK 接口也没有干净槽位)

| 缺失角色 | 代表算法 | 说明 |
|---|---|---|
| `Xof` | SHAKE128/256、SHA-3 XOF、cSHAKE、KangarooTwelve | `MessageDigest.doFinal()` 是定长语义,JDK 无「给我 N 字节输出」概念。BC 用 `Xof extends Digest` 单独表达。 |
| `DerivationFunction`(+ `DerivationParameters`) | HKDF、KDF1/2、scrypt、bcrypt、Argon2 | JCA 无第一等 KDF 接口,仅有 `SecretKeyFactory` 的 PBKDF2。BC 用 `DerivationFunction` 一族(含 `MacDerivationFunction`、`DigestDerivationFunction`)。 |

这两类 JDK 概念上也装不下,必须新增角色接口;即使给 `CryptoProvider` 加 `registerXxx`,
也得先定义这两个新角色——这正是 BC 没有把它们塞进 `Digest`/`Mac`、而是另立接口的原因。

### B 类 —— JDK 有槽位,但 flora-root 缺 BC 式轻量角色

| 缺失角色 | 代表算法 | JDK 对应槽位 | flora-root 现状 |
|---|---|---|---|
| `Agreement`(+ `BasicAgreement`) | ECDH、X25519/X448 | `KeyAgreement` | 无轻量角色 |
| `Wrapper` | AESWrap、AESWrapPad(密钥包装) | `Cipher.WRAP_MODE` | 无独立角色 |
| `KEM`(+ `Encapsulator`/`Decapsulator`) | ML-KEM/Kyber、HPKE | JDK 21+ `javax.crypto.KEM` | 无角色 |
| `EntropySource` / DRBG(`DigestRandomGenerator`、`SP80090DRBG`) | 确定性随机数 | `SecureRandom`(+ `DRBG` 实现) | 无角色 |
| `AsymmetricCipherKeyPairGenerator`(+ `KeyGenerationParameters`) | 轻量级密钥对生成 | `KeyPairGenerator` | 只有包装 JCA 的 `JdkKeyPairGenerator` |

这些 JDK 概念上能覆盖,但若要「BC 式即插即用 + 与现有角色同构」,需补成独立接口而非继续走 JCA。

### C 类 —— BC 的「可组合」角色(JDK/flora-root 用字符串折叠了)

| 缺失角色 | 表达什么 | flora-root 现状 |
|---|---|---|
| 模式/运算对象(`CBCBlockCipher`、`GCMBlockCipher`、`CTR`/`SIC`、`CFB`/`OFB`…) | 把 mode 做成可套在任意 `BlockCipher` 上的对象 | 折叠进 `"AES/CBC/PKCS5Padding"` 变换字符串,`JdkBlockCipher` 内部处理 |
| `BlockCipherPadding` | PKCS7、ISO7816、ISO10126 等填充策略对象 | 折叠进变换字符串 |
| `AEADBlockCipher` | 一等 AEAD 角色(暴露 `processAADByte`/`getOutputSize`/`doFinal`) | `JdkBlockCipher.process` 内部吞掉 GCM 标签逻辑 |
| `AsymmetricCipher`(流式非对称)+ `BufferedAsymmetricBlockCipher` | 非对称流式(ECIES 类) | 只有 `AsymmetricBlockCipher` 块式 |
| `ExtendedDigest` | `getByteLength()` 等扩展 | `Digest` 无此 |
| `Verifier` | 仅验签角色(与 `Signer` 分离) | 无 |

C 类不改变「能表达哪些算法」,只改变「怎么组合」——这正是 BC 对象组合 vs JDK 字符串变换的本质差异。

### D 类 —— 支撑型(多属 B 子类)

- `PBEParametersGenerator`(PBE → `CipherParameters`,与 KDF 关联)
- `DigestDerivationFunction` / `MacDerivationFunction`(`DerivationFunction` 子类型)
- `Commitment`(ECIES 承诺,极窄众)

## 三、概念对齐 BC 的最小必补清单(优先级)

1. **`Xof`** —— 真缺口,无法靠 JDK 槽位绕过
2. **`DerivationFunction`**(+ `PBEParametersGenerator` 等子类)—— 真缺口
3. **`Agreement` / `Wrapper` / `KEM` / `AsymmetricCipherKeyPairGenerator`** —— B 类,补齐后获 BC 式同构
4. (可选)**模式对象 / `BlockCipherPadding` / `AEADBlockCipher`** —— C 类,决定「对象组合 vs 字符串变换」风格

## 四、关键角色接口草稿方法签名(供实现参考,参照 BC)

```java
// A 类:可变长输出摘要
public interface Xof extends Digest {
    int doFinal(byte[] out, int outOff, int outLen);   // 输出 outLen 字节
    int doOutput(byte[] out, int outOff, int outLen);  // 增量吐出,可多次调用
}

// A 类:KDF / 口令派生
public interface DerivationParameters { }              // 标记接口
public interface DerivationFunction {
    void init(DerivationParameters params);
    void update(byte[] in, int inOff, int len);
    int generateBytes(byte[] out, int outOff, int len);
}

// B 类:密钥协商
public interface Agreement {
    void init(CipherParameters param);
    byte[] calculateAgreement(CipherParameters pubKey);   // 新 BC 用 byte[],旧用 BigInteger
}

// B 类:密钥包装
public interface Wrapper {
    void init(boolean forWrapping, CipherParameters params);
    byte[] wrap(byte[] in, int inOff, int len);
    byte[] unwrap(byte[] in, int inOff, int len);
}

// B 类:轻量级密钥对生成
public interface KeyGenerationParameters { int getStrength(); SecureRandom getRandom(); }
public interface AsymmetricCipherKeyPairGenerator {
    void init(KeyGenerationParameters param);
    AsymmetricCipherKeyPair generateKeyPair();
}

// C 类:AEAD 一等角色(暴露 AAD 与输出长度)
public interface AEADBlockCipher {
    void init(boolean forEncryption, CipherParameters params);
    String getAlgorithmName();
    int getOutputSize(int len);
    int getUpdateOutputSize(int len);
    void processAADByte(byte in);
    void processAADBytes(byte[] in, int inOff, int len);
    int processByte(byte in, byte[] out, int outOff);
    int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff);
    int doFinal(byte[] out, int outOff);
    byte[] getMac();
}

// C 类:填充策略对象
public interface BlockCipherPadding {
    String getPaddingName();
    void init(SecureRandom random);
    int addPadding(byte[] in, int inOff);
    int padCount(byte[] in);
    int getPaddingSize();
}
```

## 五、JDK 适配思路

- `Xof` / `DerivationFunction` / `Agreement` / `Wrapper` / `AsymmetricCipherKeyPairGenerator`
  的 `Jdk*` 适配器应包 JDK 对应类(`MessageDigest`(仅定长,需另寻 XOF 实现)、
  `SecretKeyFactory`(PBKDF2)、`KeyAgreement`、`Cipher.WRAP_MODE`、`KeyPairGenerator`)。
  `Xof` 与 `DerivationFunction` 若无 JDK 实现,可挂 BC 引擎或自定义实现(走 `registerXxx`)。
- 模式对象 / `BlockCipherPadding` / `AEADBlockCipher` 的 `Jdk*` 适配器直接复用
  `javax.crypto.Cipher` 的 transformation 字符串,仅把「对象组合」翻译成「字符串」。

## 六、待决策

1. 是否要补全 C 类(对象组合风格)?这会决定 `flora-root` 是「BC 式接口 + JDK 字符串引擎」
   还是进一步走向「BC 式接口 + BC 式对象组合」。前者改动小,后者更贴近 BC 本质。
2. `Xof` / `DerivationFunction` 的真缺口,是否引入 BC 作为可选依赖(仅提供引擎),
   还是坚持零依赖、自己实现 SM3/SHAKE 等?
3. `KEM` / `EntropySource` 是否纳入本期范围，还是仅占位接口、留待后续。→ **已决策纳入本期**：KEM 用 `AgreementBasedKem`（经典曲线真实实现），EntropySource/DRBG 用 `SecureRandomEntropySource` + `HMacDrbg`，均已实现并测试。

## 七、实施状态（2026-07-28）

已按「不引入 BC 依赖、全面对齐 BC 轻量 API」的方针落地，`flora-root` 编译与全部 1247 个测试通过。

### KEM（密钥封装机制）—— 2026-07-28 补充
- 角色接口：`KEM` + `Encapsulator` / `Decapsulator` / `SecretWithEncapsulation` + `SecretWithEncapsulationImpl`（`destroy()` 清零密钥材料）
- 真实实现 `engine.AgreementBasedKem`：以「`Agreement`（ECDH/X25519/X448/DH）+ `DerivationFunction("HKDF")`」构造经典 KEM。
  封装=生成临时密钥对 → 用接收方公钥协商得共享秘密 Z → HKDF 派生 32 字节对称密钥，临时公钥编码作为封装密文；
  解封装=从封装密文经 `KeyFactory` 重建临时公钥 → 相同协商+派生得同一密钥。
- 占位 `PlaceholderKem`：未知算法（如后量子 ML-KEM）抛 `UnsupportedOperationException`
- `CryptoProvider.kem(name)`：对 `ECDH`/`X25519`/`X448`/`DH` 默认 `AgreementBasedKem`，其余 `PlaceholderKem`；含 `registerKem`

### EntropySource / SP800-90A DRBG —— 2026-07-28 补充
- 角色接口：`EntropySource`（熵源，`getEntropy(numBits)`、`entropySize`、`isPredictionResistant`）、
  `SP80090DRBG`（`generate`/`getBlockSize`/`reseed`）
- 真实实现 `engine.SecureRandomEntropySource`（包 `SecureRandom`，默认抗预测）、
  `engine.HMacDrbg`（NIST SP800-90A §10.1.2 HMAC_DRBG，纯 Java，以任意 `Mac` 为原语；
  提供「熵源实时取熵」与「固定熵/nonce 可复现」两种构造）
- `CryptoProvider.entropySource()` / `entropySource(name)` / `hmacDrbg(hmacAlgorithm, securityStrengthBits, personalizationString)`；
  含 `registerEntropySource` / `registerDrbg`
- 注：JDK 仅 `SecureRandom`，无第一等 DRBG 抽象，故 `HMacDrbg` 为自实现（零依赖）

### 新增角色接口（core）
- 摘要扩展：`ExtendedDigest`（`JdkDigest` 已实现，含 `getByteLength`）、`Xof`（可变长输出）
- 非对称流式：`AsymmetricCipher` + `BufferedAsymmetricBlockCipher`（包裹 `AsymmetricBlockCipher`）
- 密钥包装/协商：`Wrapper` + `JdkWrapper`（Cipher WRAP_MODE）、`Agreement` + `JdkAgreement`（KeyAgreement）
- 派生/口令：`DerivationParameters` / `DerivationFunction` / `DigestDerivationFunction` / `MacDerivationFunction`；
  纯 Java 真实实现 `Kdf2DerivationFunction`（KDF2）、`HkdfDerivationFunction`（HKDF）；
  `PBEParametersGenerator` + `JdkPBEParametersGenerator`（JDK PBKDF2）
- 轻量密钥生成：`AsymmetricCipherKeyPair` / `KeyGenerationParameters` / `AsymmetricCipherKeyPairGenerator` + `JdkAsymmetricKeyPairGenerator`
- 填充/模式：`BlockCipherPadding` + `PKCS7Padding` / `ISO7816d4Padding` / `ZeroBytePadding`；
  `PaddedBufferedBlockCipher`；模式 `CBCBlockCipher` / `CFBBlockCipher` / `OFBBlockCipher` / `SICBlockCipher`（纯 Java 链式）；
  `AEADBlockCipher` + `JdkAeadBlockCipher` / `GCMBlockCipher`
- 标记：`Verifier extends Signer`

### 占位实现（JDK 概念缺口）
- `PlaceholderXof`：XOF（JDK 无「N 字节输出」槽位），可变长方法抛 `UnsupportedOperationException`
- `PlaceholderDerivationFunction`：通用 KDF（JCA 无第一等 KDF 抽象），`generateBytes` 抛 `UnsupportedOperationException`
- `KDF2` / `HKDF` 已预注册，按名（`CryptoProvider.derivationFunction("KDF2"|"HKDF")`）即可用

### CryptoProvider 扩展
新增 `registerXxx` / 工厂：`extendedDigest`、`xof`、`asymmetricStreamCipher`、`wrapper`、`agreement`、
`derivationFunction`、`pbeParametersGenerator`、`blockCipherPadding`、`aeadBlockCipher`、`asymmetricKeyPairGenerator`。
JDK 无能力者默认回退占位实现。

### 测试
`CryptoRolesTest` 覆盖上述新角色（Wrapper 往返、ECDH 协商、PBKDF2 与 JDK 对齐、KDF2/HKDF 自洽、
AES-GCM AEAD 往返、CBC/SIC 模式链式、PKCS7 填充、RSA 流式非对称、Xof/派生占位抛异常；
补充 KEM 的 ECDH/X25519 往返、secret `destroy()` 清零、未知算法占位抛异常，
EntropySource 取熵长度、HMAC_DRBG 确定性/个性化/工厂生成）。
