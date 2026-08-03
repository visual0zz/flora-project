# 决策：SSH 加密算法统一走 flora crypto 界面（Phase 3 适配层）

日期：2026-08-03
模块：flora-root / com.flora.ssh
编号：decision20260803-04

## 背景

吸收进 `flora-root` 的 JSch 库此前直接调用 JDK `javax.crypto` / `java.security`
（`jce/` 后端，约 72 个类）。用户要求：**加密算法调用必须走 `com.flora.crypto`
界面**。此前已确认走「架构优先（Route 2）」路线：JDK 原生能力用 flora crypto 包内
新增的转发实现类接入 flora 界面。

## 决策

1. **在 `com.flora.crypto.core.engine` 新增两个 JDK 转发实现类**：
   - `JdkSignature`：包装 JDK `java.security.Signature`（RSA/DSA/ECDSA/EdDSA 全套）。
   - `JdkKem`：包装 JDK 21+ `javax.crypto.KEM`（ML-KEM 封装/解封装），实现 flora 的
     `KEM` 界面，取代 `PlaceholderKem`。

2. **新建适配包 `com.flora.ssh.crypto`（内部实现，不导出）**：
   - 18 个基类：`FloraCipher`(CBC/CTR)、`FloraAeadCipher`(GCM)、`FloraMac`、`FloraDigest`、
     `FloraSignatureRsa/Dsa/Ecdsa/EdDsa`、`FloraDh`、`FloraXdh`、`FloraEcdh`、`FloraKem`、
     `FloraRandom`、`FloraPbkdf2`、`FloraKeyPairGenRsa/Dsa/Ecdsa/EdDsa`。
   - 49 个薄子类，一个算法一个类，全部委托 flora crypto。
   - 协议逻辑（SSH 线格式、ASN.1↔mpint、RFC 7748 小端编码、RFC 8268 范围检查）保留在适配层。

3. **删除 `jce/` 后端**，改写 `JSch.java` 静态注册表，全部指向 `crypto.*`。

4. **摘除悬空/不可用条目**：`bc.*`（BouncyCastle 已删）、`jgss.*`（已删）、`arcfour`。
   涉及的算法（chacha20-poly1305、cast128、twofish、seed、hmac-ripemd160、Argon2、
   scrypt、sntrup761、RC4）非 JDK 原生且难验证，**留到最后**再补。

## 关键发现

- Phase 1 折叠 MR 源码集时**漏掉了 EdDSA/XDH/ML-KEM 的真实实现**，`jce/XDH`、
  `jce/SignatureEdDSA`、`jce/MLKEM*` 全是抛 `UnsupportedOperationException` 的 stub。
  这意味着 curve25519 / ssh-ed25519 / mlkem768 在运行时实际不可用。本次适配用 flora
  `JdkAgreement`/`JdkKeyPairGenerator`/`JdkKem` 补齐了真实实现（对照 `absent/jsch`
  的 java11/java15/java24 MR 源码集移植）。

- `FloraAeadCipher` 的 GCM IV 计数器（RFC 5647 隐式 IV，12 字节 IV 偏移 4 处 64 位计数）
  递增会修改保存的 IV 数组，因此 `init` 中**防御性克隆** key/iv，避免污染调用方数组。

## 验证

- 全项目 `mvn compile` 通过（零外部依赖）。
- `flora-root` 全量单元测试通过。
- 新增 `CryptoAdapterSmokeTest`（14 个用例）：GCM 多包往返+跨包计数器、AES-CBC/CTR 往返、
  HMAC/SHA-256 已知答案、Ed25519/RSA 签名往返、X25519/DH 双方密钥一致、ML-KEM 封装/解封装、
  GCM 与 JDK 双向交叉验证。

## 后续

- 非 JDK 原生算法（复杂二进制运算、难以用测试向量验证）留到最后实现。
- `keypairgen_fromprivate.eddsa` 仍指向会抛 `UnsupportedOperationException` 的默认方法
  （从现有私钥字节生成密钥对的能力缺失，`bc.KeyPairGenEdDSA` 被删前同样不可用）。

## 补充（Phase 0 完成情况，2026-08-03）

留到最后的算法已全部实现并通过 RFC 向量自测（放 `com.flora.crypto.core.engine`，
参数类型放 `com.flora.crypto.core`，未动 `Schemes`）：

| 算法 | 位置 | 测试向量 |
|---|---|---|
| RIPEMD-160 | `Ripemd160Digest` | RFC 2286 标准向量 |
| BLAKE2b-256/512 | `Blake2bDigest` | RFC 7693 |
| Poly1305 | `Poly1305Mac` | RFC 8439 §2.5.2 |
| ChaCha20 + ChaCha20-Poly1305 | `ChaCha20Engine` / `ChaCha20Poly1305` | RFC 8439 §2.3/2.4/2.8 |
| scrypt | `Scrypt` + `ScryptParameters` | RFC 7914 §12 |
| Argon2d/i/id | `Argon2` + `Argon2Parameters` | RFC 9106 §5 |
| HMAC（支持空密钥） | `HMac` | RFC 4231 + 空密钥向量 |

调试中修正的关键实现错误：RIPEMD-160 右通道轮函数反向；BLAKE2b finalize 无
`h[0]^=~0`、计数器先累加、块对齐消息的"延迟压缩"语义；ChaCha20 counter 占 1 字、
nonce 占 3 字；Argon2 trunc 乘法需无符号扩展、pass>0 的 XOR 语义、lane 首块 prev 环绕、
独立索引计数器从 1 起、finalize 用 H' 而非 H。
