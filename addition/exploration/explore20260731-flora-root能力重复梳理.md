# flora-root 能力重复梳理

> 日期：2026-07-31
> 范围：flora-root 模块内部 + 跨模块（flora-sanctum / flora-ramet / flora-tangle / flora-osmetes）

---

## 一、模块内部重复（flora-root 自身）

### 1. 元组体系双轨制（最明显）

| 包 | 规模 | 定位 |
|---|---|---|
| `com.flora.container.tuple` | 22 文件（Tuple + Tuple1~Tuple20 泛型） | 泛型元组 |
| `com.flora.fast.container.tuple` | 148 文件（FastTuple + FastTupleXXX 类型专门化组合） | primitive 专用元组 |

两个包都被 `module-info.java` 导出（第 21、24 行）。**职责完全重叠**：同一"元组容器"能力存在两套 API，泛型版（Tuple1-20）与专门化版（FastTupleBB/III/LLLL…）并存。如果 `fast` 版是超集且性能更优，泛型版很可能是历史遗留。

### 2. 加密原语双栈并存

`com.flora.crypto.core`（自研 BC 风格库）与 `com.flora.communication.crypto` + `communication.jce`（72 文件）**重复实现同一批算法**：

- **分组密码**：`JdkBlockCipher` + `mode/`（CBC/CFB/GCM/OFB/SIC）↔ JSch `Cipher` + Aes128Cbc/Aes256Gcm/Blowfish…
- **MAC/摘要**：`JdkMac`、`JdkDigest` ↔ `FloraMac`、HmacSha256/Md5/Sha1…
- **密钥派生**：`Pbkdf2ParametersGenerator`、`HkdfDerivationFunction`、`Kdf2DerivationFunction` ↔ `FloraPbkdf2`（5.1KB 自研 PBKDF2）+ JSch 自带 `PBKDF2`/`Argon2`/`BCrypt`/`SCrypt`/`KDF`
- **非对称/密钥交换**：`JdkSignature`、`JdkAgreement`、`JdkKem` ↔ `FloraSignature*`、`FloraDh`、`FloraKem`、`KeyPair*`、`DH*` 全套

注意 `communication/crypto` 下的 `Flora*` 类（FloraCipher、FloraMac、FloraPbkdf2 等 1~6KB）明显是"桥接 JSch → crypto.core"的适配层——说明这个收敛方向**已经开始了**，但 JSch 自带的那套原语（jce/、jbcrypt/、根级 Argon2/BCrypt/SCrypt）仍然完整保留。JSch 内部甚至自己把 `BCrypt` 标为 `@Deprecated`（"Use KDF instead"），可见这套旧原语体系本身就是冗余的。

### 3. 哈希能力三处并存

- `com.flora.entropy.HashUtil`（门面 → StandardHash：MD5/SHA1/SHA256/SHA512/SHA3 + MurmurHash + GoldenRatioMix）
- `com.flora.crypto.core.engine.JdkDigest`（JCE 摘要封装，经 CryptoProvider 注册）
- `com.flora.communication` 的 `Sha1/Sha256/Sha512/Md5` 等（JSch 第三方）

前两处都是自研 SHA/MD5 门面，能力重叠，且 `HashUtil` 与 `CryptoProvider` 都靠 `@SuitedFor`/`AlgorithmFamily` 标注场景，概念上也在竞争。

### 4. 词法器只有一套是"活"的

`com.flora.syntax`（Tokenizer/Token/TokenType，自称"**共享**通用词法器"）目前没有任何实际消费者——flora-ramet 用自己的 Lexer，不在这里。

### 5. 次要/概念级重叠

- **hex/字节工具**：`codec.HexUtil`（✅ 已收敛——`java.BytesUtil.bytes2hexString` 正确委托它）vs `communication.Util`/`Buffer`（JSch 自带，第三方）
- **JSON Schema**：`codec.jsonschema`（校验）与 `mock.jsonschema`（生成）功能互补不重复，但各自实现类型系统（如 `JsonTypes` vs 生成器内部类型判断）
- **日志**：`runtime.log`（自研）vs `communication.logging`（JSch，1 文件）
- **随机源**：`entropy.Entropy`（信息熵估算）与 `crypto.core.engine.SecureRandomEntropySource`/`HMacDrbg`（DRBG 熵源）——概念接近但职责不同

---

## 二、跨模块重复（flora-root vs 消费方）

### 6. flora-sanctum.crypto 重复造轮子（最值得处理）

sanctum 已依赖 flora-root（用 `JsonUtil`），但 `sanctum/crypto` 却用 JDK JCE 重新实现了 root 里已有的能力：

| sanctum 自研 | flora-root 已有 |
|---|---|
| `AesGcmCipher`（AES-256-GCM） | `JdkBlockCipher` + `GCMBlockCipher` |
| `HmacSigner`（HMAC-SHA256） | `JdkMac` |
| `KeyDerivation`（PBKDF2-HMAC-SHA256） | `Pbkdf2ParametersGenerator` |
| `SecureRandomHolder` | `SecureRandomEntropySource`/`HMacDrbg` |

消费方用 JDK 原生 JCE 自己实现，而基础库提供同能力——是"该复用而未复用"的典型。

### 7. flora-ramet 的 Lexer 未复用 root 的 Tokenizer

`ramet/engine/Token` + `parser/Lexer`（模板专用，16 种 Token 类型）与 `root/syntax`（通用）名字撞车（两个 `Token` 类）。ramet 的 Lexer 是领域强绑定（`<#if>`、`${}`、Lson 参数），直接复用通用 Tokenizer 需要适配，但 root 的 syntax 包既然定位"共享"，两者至少应建立引用关系而非完全平行。

### 8. flora-tangle 的 ByteArrayBuilder（轻微）

`tangle/classfile/ByteArrayBuilder`（大端序分块写）与 `root/java/BytesUtil`（小端序转换）方向互补、能力部分重叠。tangle 已依赖 flora-root，但 ByteArrayBuilder 是包私有类，未造成对外 API 重复，可接受。

---

## 三、已正确复用的范例（作为对照基准）

- sanctum `JsonCodec` → 委托 `root codec.JsonUtil` ✅
- root `BytesUtil.bytes2hexString` → 委托 `HexUtil` ✅
- root `crypto.core` 内部用 `java.CheckUtil`/`tag.ThreadFragile` ✅

---

## 四、建议优先级

| 优先级 | 重复项 | 建议 |
|---|---|---|
| **高** | `container.tuple` 与 `fast.container.tuple` | 二选一或明确分层：泛型版作为 API，fast 版收进 `impl` 子包不导出 |
| **高** | sanctum `crypto` 包 | 改委托 root `crypto.core`（先确认 root 的 API 覆盖度：GCM 输出格式、PBKDF2 参数签名是否对齐） |
| **中** | `communication` 中 JSch 自带 KDF/jce 体系 | 评估能否彻底移除，只保留 Flora* 桥接层 + `crypto.core`（需回归 SSH 集成测试） |
| **中** | `entropy.HashUtil` 与 `JdkDigest` | 收敛为一个摘要门面 |
| **低** | root `syntax` 与 ramet Lexer | 关系明确化：复用、合并或各自保留并在文档说明 |
