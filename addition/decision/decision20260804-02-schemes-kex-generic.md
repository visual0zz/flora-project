# 决策 2026-08-04：KeyExchange 接口通用化

## 决策

`com.flora.crypto.schemes.keyexchange.KeyExchange` 接口**只反映"密钥交换"这一数学行为的本质**，
采用多轮「贡献 → 贡献 / 共享密钥」模型：

```java
public interface KeyExchange extends Scheme {
    void init(SchemeContext ctx) throws SchemeException;
    byte[] step(byte[] peerContribution) throws SchemeException; // 首轮传 null；完成轮返回 null
    boolean isComplete();
    byte[] sharedSecret();
}
```

- 不绑定任何具体协议（SSH/TLS）的传输、报文、`exchangeHash`、版本串 `V_C/V_S/I_C/I_S`、
  或对端签名验证。
- 角色（initiator/responder）不强制进接口，由"谁先 `step(null)`"约定。
- `SchemeContext` 简化为仅含 `EntropySource entropy()`；`MessageTransport` 保留在
  `schemes.transport` 但 KEX 算法族不使用（属上层组合级编排/AKE 族关注点）。
- engine 命名去除协议耦合前缀（如 `SshDhGroup14Sha256` → `DhGroup14`）。

## Why

用户在审查方案时指出初版 `KeyExchange` 把 SSH 的报文状态机、`V_C/V_S/I_C/I_S`、`exchangeHash`
焊死在接口中，那其实是"SSH 密钥交换协议"而非"密钥交换"行为本身。用户要求接口通用，
保证**未来发明新的数学本质的密钥交换算法（后量子 KEM、同源、码、新型格结构等）也能接入**。

## How to apply

- 后续实现 `KeyExchange` 的具体算法（DH/ECDH/X25519/KEM-KEX/未来结构）必须只产出
  `sharedSecret()`，不得把协议层哈希、报文拼装、身份认证塞回该接口。
- SSH 的 `H` 计算、服务端签名校验、`KEXINIT` 协商等，必须放在构建于 `KeyExchange` 之上的
  **组合级协议编排**（AKE 族，或 SSH 模块），不得回灌进算法级接口。
- 相关方案：见 `addition/design/idea20260804-schemes-abstraction.md`（§3.2）。
