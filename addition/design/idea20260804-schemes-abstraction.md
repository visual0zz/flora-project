# 方案：schemes 包的协议编排抽象

## 1. 定位与核心洞察

`com.flora.crypto.core` 抽象的是**密码学原语**：无状态或短生命周期、单次调用即出结果
（`Digest`、`BlockCipher`、`Agreement`、`KEM` …）。这些原语的接口契约是"喂入数据 → 产出结果"。

`com.flora.crypto.schemes` 抽象的是**密码学协议/方案（scheme）**：有状态、按步骤推进、
依赖运行上下文（熵源、传输/IO、对端消息），并**组合多个 core 原语**产出复合安全结果。

因此 schemes 复用 core 的**元模式**（族接口自述 + 注册表分发裁决），但协议族接口必须表达
"有状态、分步推进"这一原语没有的特质。

**分层约定**：schemes 内部存在两层——
- **算法级协议族**：如 `KeyExchange`，只反映该密码学行为的数学本质（如"双方贡献→共享密钥"），
  不绑定任何具体协议（SSH/TLS）的报文、哈希、版本串。
- **组合级协议编排**：如认证密钥交换（AKE）、SSH/TLS 的 KEX 流程，构建在算法级协议族之上，
  负责拼装报文、计算 exchange hash、对端身份认证等。这类编排是 schemes 的另一类族（未来新增）。

## 2. 与 core 结构的一一映射

| core（`com.flora.crypto.core`） | schemes 对应 | 说明 |
| --- | --- | --- |
| `AlgorithmFamily`（基接口） | `Scheme`（基接口） | 自述 `getAlgorithmName()` / `supportedAlgorithms()` / `priority()`，供注册表读取 |
| `Digest` / `Mac` / `Agreement` …（各专门族接口） | `KeyExchange` / `SecureChannel` / `PeerAuthenticator` / `KeySchedule` …（各协议族接口） | 每个族定义自己的生命周期契约 |
| `core.engine.*`（原语实现，如 `JdkAgreement`） | `schemes.engine.*`（协议编排实现，如 `DhGroup14`） | 实现组合 core 原语，**不再自带大整数/摘要运算** |
| `CryptoProvider`（注册 + 按名分发裁决） | `SchemeProvider`（注册 + 按名分发裁决） | 协议名与原语名属不同命名空间，独立注册表更准确 |

## 3. 抽象分层

### 3.1 `Scheme` 基接口（类比 `AlgorithmFamily`）

仅承载静态自述信息，无状态，可直接被 `SchemeProvider` 读取注册：

```java
public interface Scheme extends AlgorithmFamily {
    // 继承: String getAlgorithmName(); Set<String> supportedAlgorithms(); int priority()
    // 不定义任何运算方法——生命周期由各协议族接口声明
}
```

### 3.2 `KeyExchange` 协议族接口（通用，只反映密钥交换行为）

**设计原则**：接口只表达密钥交换的数学本质——双方各贡献一个公开值/密文，最终算出共享密钥；
**不绑定**任何传输、报文、协议版本串、exchange hash 或对端认证。未来任何新数学本质的密钥交换
（mod p DH、ECDH、X25519、后量子 KEM-KEX、同源/码/新型格结构）只要能表达为
「贡献 → 贡献 / 共享密钥」即可接入。

```java
public interface KeyExchange extends Scheme {
    /** 注入运行环境（至少含熵源）；不绑定任何具体协议上下文 */
    void init(SchemeContext ctx) throws SchemeException;

    /**
     * 推进一轮密钥交换。
     * @param peerContribution 对端上一轮的公共贡献（线格式字节）；首轮传 {@code null}
     * @return 本端本轮应发送给对端的公共贡献；
     *         完成步在发起方返回 {@code null}；在响应方（本端贡献尚未发出）返回本端贡献
     */
    byte[] step(byte[] peerContribution) throws SchemeException;

    boolean isComplete();

    /** 最终共享密钥材料（原始，未经任何协议层 KDF/哈希）。 */
    byte[] sharedSecret();
}
```

- **角色**：不强制进接口——谁先 `step(null)` 谁即发起方（initiator）；响应方先 `step(peer)`。
- **多轮**：发起方 `step(null)` 发出本方贡献 → 收到对端贡献后 `step(peer)` 算出共享密钥并返回 `null`；
  响应方收 `step(peer)` 时**在同一步既算出共享密钥、又返回本方贡献**（因本端贡献尚未发出）。
  天然支持 2-pass/3-pass KEX，且不要求算法实现区分 initiator/responder 角色。
- **贡献类型**：统一为 `byte[]`（算法线格式），协议层负责将其装入自己的报文。

#### 3.2.1 通用性证明（覆盖不同数学本质）

| 算法类别 | init / step(null) | step(peer)（发起方视角） | step(peer)（响应方视角） |
| --- | --- | --- | --- |
| mod p DH（group1/14/15/16/18） | 生成私钥 x，返回 `e = g^x mod p` | 用 `f` 算 `K = f^x mod p`，返回 null | 同左算出 K，并**返回 `e`** |
| ECDH / X25519 / X448 | 生成密钥对，返回公钥 | 用对方公钥算共享点，返回 null | 同左算出共享点，并**返回公钥** |
| KEM-based KEX（Kyber 等后量子） | 封装，返回密文 c + 保留解封装密钥 | 解封装得共享密钥，返回 null | 解封装得共享密钥，并**返回密文** |
| 未来新结构（同源/码/新型格） | 只要能表达为「贡献→贡献/共享密钥」即可接入 | 同上 | 同上 |

#### 3.2.2 SSH 特有概念剥离（不再进入 KeyExchange 接口）

- `exchangeHash(H)`、版本串 `V_C/V_S`、KEXINIT `I_C/I_S` → 由构建于 `KeyExchange` 之上的
  **认证密钥交换编排**（AKE 族，或 SSH 模块）负责：拿到 `sharedSecret()` 后自行 `digest` 算 H。
- 服务端签名验证 → 对端身份认证，属上层 AKE/协议编排。
- 因此 engine 命名去掉协议耦合的 `Ssh…Sha256` 前缀，纯算法命名为 `DhGroup14`
  （SHA-256 是 SSH 编排层对 H 的选择，与算法无关）。

### 3.3 `SchemeContext`（schemes 独有，core 无对应物）

原语无状态故不需要；协议需要统一注入运行环境。KEX 算法本质只需熵源：

```java
public interface SchemeContext {
    EntropySource entropy();   // 取自 core 的熵源族
}
```

> 注：`MessageTransport`（收发消息抽象）仍保留在 `schemes.transport`，但 **KEX 算法族不使用**——
> 它是更上层的组合级协议编排（AKE 族）或具体协议（SSH/TLS）的关注点，用于解耦 `Session` 之类
> 的传输实现。算法级协议族保持与传输无关。

### 3.4 `engine` 实现（类比 `core.engine`）

```
com.flora.crypto.schemes.engine
    ├─ kex/                        // 算法级 KeyExchange 实现
    │    ├─ DhGroup14.java        // 对应现有 DHG14N（底层走 CryptoProvider.agreement("DH")）
    │    ├─ DhGroup14Sha1.java    // 对应现有 DHG14（仅示意，sha1 由上层编排用）
    │    └─ EcdhX25519.java       // 对应现有 DH25519
    ├─ ake/                        // 组合级：认证密钥交换编排（未来）
    ├─ tls/                        // 未来：Tls13KeySchedule 等
    └─ ...
```

`DhGroup14` 内部直接使用 `JdkAgreement.of("DH")`（`Agreement` 的 JDK 适配器）算 `K`，
**不再算 `H`、不再验证签名**——这些由上层编排完成。注意：`CryptoProvider` 当前仅注册了
`"ECDH"` 而未注册 `"DH"`，所以此处直接取 `JdkAgreement` 而非 `CryptoProvider.agreement("DH")`；
未来若要在 `CryptoProvider` 注册 `"DH"`，可改走统一入口。这直接回答"DHG14N 能否被 crypto 接口描述"：
**能，且作为算法级 scheme 落地，协议相关部分交给上层编排**。

> **测试钩子与 JDK FFC 归约陷阱**：`DhGroup14` 提供包级 `init(PrivateKey)` 钩子用于向量验证。
> 但 JDK 26 的 FFC 校验会对「导入」的 `DHPrivateKeySpec` 私有指数做归约（小指数甚至被塌缩为
> `x ≡ 1 (mod q)`），导致 `e = g^x mod p` 退化为平凡值。故 `DhGroup14Test` 改用
> `KeyPairGenerator` + 带种子 `SecureRandom` 生成**确定性**密钥对，再读取其实际 `x` 做独立
> `BigInteger` 模幂交叉验证——既走通与生产一致的真实协商路径，又使向量可复现。手捏裸指数注入
> 的验证方式在此 JDK 下无效。

### 3.5 `SchemeProvider`（类比 `CryptoProvider`）

独立注册表，分发语义与 `CryptoProvider` 一致（「名 → 多实现条目 → 优先级 → 具体度」裁决），
按协议族分别持有注册表：

```java
public final class SchemeProvider {
    private static final Map<String, List<Entry<KeyExchange>>> KEX_REGISTRY = new ConcurrentHashMap<>();
    public static void register(KeyExchange proto, Function<String, ? extends KeyExchange> f) { ... }
    public static KeyExchange keyExchange(String name) { ... } // 同 CryptoProvider 的 priority/specificity 裁决
}
```

## 4. 待定决策（影响落地方式）

- **D1：是否抽取通用 `Registry<T>` 裁决机制？**（已决：不抽取）
  `SchemeProvider` 独立复制裁决语义，不改动 `CryptoProvider` 核心。

- **D2：现有 JSch KEX 与新建 schemes 的关系**（已决：并行新增 + 最后迁移）
  新建 `schemes.engine.kex.*` 与现有 `com.flora.comm.ssh.DHGN` 一族并行存在，
  验证通过后再切换 `Session` 引用、废弃 JSch 老实现。

- **D3：KeyExchange 接口通用化**（已决：通用、只反映 KEX 行为）
  接口采用多轮「贡献→贡献/共享密钥」模型，剥离 SSH 特有（exchangeHash/报文/版本串/签名验证）
  到上层组合级编排；保证未来新数学本质算法可接入。见 `decision20260804-02-schemes-kex-generic.md`。

- **D4：是否保留 `Schemes.java` 占位类**
  本方案不改动现有 `Schemes.java` 占位入口，新抽象以新接口/新子包落地。
  待结构稳定后，可把 `Schemes.java` 改为指向 `SchemeProvider` 的薄门面，或保留作包锚点。

## 5. 演进性

- **新增未来密钥交换算法** = 新增一个 `engine.kex.*` 实现 + 在 `SchemeProvider` 注册，
  不动 `KeyExchange` 接口（接口已对数学本质开放）。
- **新增新协议大类**（如 AKE、SecureChannel）= 新增一个族接口，不侵入现有族。
- **命名按 `engine.<协议族>.<具体算法>`**，语义层级清晰，符合两层/三层包结构规范。
- 全部仅组合 core 已有原语，**零外部依赖**，与当前 flora-root 的依赖面约束一致。
