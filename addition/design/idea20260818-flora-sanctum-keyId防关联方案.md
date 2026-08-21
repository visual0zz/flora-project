# flora-sanctum keyId 防关联定位方案设计（定稿）

日期：2026-08-18
类型：设计定稿（参数已确认）
状态：块格式与参数已定，待实现

> 关联探索成果：`addition/exploration/portalFunction/explore20260818-自反函数seed反推困难探索.md` 第 7 节。

## 1. 背景与目标

现状密文块头 keyId 定位候选密钥（`KeyIdIndex` 查表 + GCM-SIV tag 确证）：
`keyId = byte1(8bit 随机) + SHA256(DEK‖byte1)[0:3]`，**4 字节、同 DEK 仅 256 种**。

**目标**：防关联——攻击者拿大量密文，无法判断"哪些密文用了同一密钥"。
实验表明现状在大量密文下防关联失效（同 DEK 加密 10000 次 keyId 重复 9744 次）。

**定稿约束**：
- 同一块格式同时承载**数据存储**（内部对象）与**外部加密数据**（外部密钥密文）。
- 块头开销最小化：keyId **8 字节**，seed **复用 nonce**（零额外字段）。

## 2. 现状分析

- 块格式（`store/BlockFormat`）：`magic(8)+version(1)+flags(1)+uuid(16)+keyId(4)+nonce(12)+ct+tag`，HEADER_LEN=42。
- **AAD 已覆盖整个密文头（含 keyId/nonce）‖ timestamp**（`crypto/impl/CipherCodec`）——密文头篡改已被 GCM-SIV 认证。新格式保持该保证。
- `KeyIdIndex`：每 DEK 预计算 256 条 keyId 条目（内存索引）；`CipherCodec.makeKeyIdWith` 随机 byte1。
- KEK/HKDF 派生入口：`crypto/KeyDerivation`（HKDF-SHA256，已有 `derive(ikm, salt, info, len)`）。
- root group 已支持 `type=root` 与 `repoKeyIdSeed` 字段（`VaultCreator`/`VaultUnlocker`/`Vault` 已实现）。

## 3. 方案设计（定稿）

### 3.1 仓库级 keyId 派生种子（repoKeyIdSeed）

```
repoKeyIdSeed = CSPRNG 32 字节（VaultCreator 创建时生成，随 DATA root group json 用 KEK 加密存储）
```

- **存储**：DATA root group json 的 `repoKeyIdSeed` 字段（base64），root group 块本身用 KEK 加密 → 攻击者未解锁不可读。
- **解锁**：`VaultUnlocker` 发现 DATA root 时读取 → `Vault.repoKeyIdSeed()`；锁定/退出 `clearSecrets` 清零。
- **跨主密码变更稳定**：主密码变更仅重包裹 root group（`MasterKeyRotator`），`repoKeyIdSeed` 作为字段值保持不变。
- **旧库兼容**：无此字段 → `repoKeyIdSeed()` 返回 null（调用方按未启用处理）。

### 3.2 keyId 生成与恢复（镜像 Feistel 对合，8 字节块）

```
dekId = SHA-256(DEK)[0:8]                  // 8 字节内部标识（索引键）
seed  = nonce（12 字节，GCM-SIV 参数，直接复用）
keyId = f(repoKeyIdSeed ‖ nonce, dekId)     // 镜像 Feistel，8 字节块，7 轮，HMAC 轮函数
恢复：dekId = f(repoKeyIdSeed ‖ nonce, keyId) // 对合可逆，O(1) 定位
```

- 轮函数 `F(参数, 半块) = HMAC-SHA256(参数, 半块) 截断 4 字节`（半块 4 字节）。
- 7 轮（轮序列 L,R,L,R,L,R,L）。
- **参数定稿依据**：
  - `seed = nonce`：12 字节（96 位）随机，防关联碰撞在 100 万次同密钥加密下为 0（实验）——**零额外字段**。
  - `keyId = 8 字节`（64 位输出空间）：同密钥加密 `2^30` 次碰撞才到 `2^-5`；4 字节（32 位）在 3 万次加密即 10% 碰撞（外部加密接口现实可达）——**4 字节不足，8 字节为安全底线**。
  - `dekId = 8 字节`：索引碰撞 `k²/2^65`（k=100 时 `≈2^-51`），可忽略；且 8 字节验证器泄露比 32 字节少。
- 对合价值：keyId 随机化（nonce 驱动，防关联）+ **O(1) 定位**（可逆恢复 dekId），单向随机化方案会退化为 O(k) 遍历。

### 3.3 块格式定稿（VERSION_2）

```
VERSION_2 密文块头 = magic(8) + version(1)=2 + flags(1) + uuid(16) + nonce(12) + keyId(8)
HEADER_LEN_V2 = 46        （对比现状 42，仅 +4 字节）
完整块 = 头 + ciphertext + tag(16)
```

- **nonce 置于 keyId 前**，明确解析顺序：先读 nonce（作 seed）→ 再读 keyId。
- seed 复用 nonce，无独立 seed 字段。
- 同一格式承载内部对象块与外部加密数据块（同结构、同机制、同防关联强度）。

### 3.4 AAD 集成

- nonce/keyId 均位于密文头 → 随整个 header（含 version=2 与时间戳）进入 GCM-SIV AAD（现状机制不变）。
- 保证：攻击者篡改/调换 nonce 或 keyId → tag 认证失败，连试解都不发生。

### 3.5 解密流程（V2 块）

```
1. 按 version 解析头：V2 → 读 nonce(12) + keyId(8)（nonce 在前）
2. dekId = f(repoKeyIdSeed ‖ nonce, keyId)                 // 对合恢复内部标识
3. dekIdIndex.lookup(dekId) → 候选 DEK（1 条/DEK，碰撞可忽略）
4. GCM-SIV 试解（AAD = 头 ‖ timestamp，tag 确证，与现状一致）
```

## 4. 代码改动映射

| 文件 | 改动 |
|---|---|
| `store/BlockFormat` | 新增 `HEADER_LEN_V2=46`、`KEYID_LEN_V2=8`（nonce 在前）；V1 常量保留 |
| `crypto/impl/CipherCodec` | `encode` 写 V2 头（nonce→keyId 顺序）；`decode` 按 version 分支；AAD 覆盖 V2 头 |
| `crypto/impl/KeyIdIndex` | 新增按 dekId 键的索引（1 条/DEK）；保留 V1 的 4 字节 keyId 索引（兼容读旧块） |
| 新增 `crypto/impl/Involution.java` | 镜像 Feistel 对合（8 字节块、7 轮、HMAC 轮函数） |
| 新增 `crypto/KeyIdDeriver` | `makeKeyId(repoKeyIdSeed, nonce, dek)` → keyId；`resolveDekId(repoKeyIdSeed, nonce, keyId)` → dekId |
| `model/ExternalKeyService` | 外部加密走新 keyId 派生；解密走恢复 dekId 定位 |
| 内部对象写入路径（ObjectTree/EntryNode/各 Tree） | 统一走新 keyId 派生（与外部加密同机制） |

## 5. 安全分析（攻击面缓解）

| 攻击面 | 缓解 |
|---|---|
| repoKeyIdSeed 落盘/备份泄露 | 存 DATA root group json（KEK 加密存储），未解锁不可读 |
| nonce 可预测/重复 | GCM nonce 由 CSPRNG 12 字节生成（现状机制），重复概率可忽略 |
| 密文头 (nonce,keyId) 篡改/调换 | 3.4 纳入 AAD，tag 认证失败 |
| dekId 泄露（repoKeyIdSeed 泄露时） | 8 字节验证器，DEK 32 字节 CSPRNG 不可枚举；密文 tag 本就提供验证 |
| 跨密钥关联 / 跨仓库关联 | keyId 8 字节随机 + repoKeyIdSeed 每仓库独立 |
| 内部对象文件夹归属关联 | 统一格式后内部块 keyId 亦随机化（同文件夹块不可关联） |
| 错误 oracle | 解密失败统一 "decrypt failed"，不含 nonce/keyId/dekId |
| 内存滞留 | repoKeyIdSeed、dekId 索引、DEK 随锁定/退出清零 |
| 对合结构可区分（双向 oracle） | 7 轮镜像 Feistel（3 轮可区分，≥5 轮安全） |

## 6. 兼容与迁移

- **双版本共存**：VERSION_1 密文继续用 4 字节 keyId 路径读（旧 KeyIdIndex 保留）；新密文 VERSION_2 用新路径。
- **旧库 repoKeyIdSeed**：无字段 → `Vault.repoKeyIdSeed()` 为 null；V2 密文写路径要求非空，旧库写入 V2 前需先补种（或仅新库启用 V2）。
- 主密码变更不影响历史 V2 密文（repoKeyIdSeed 随 root group 重包裹保留）。

## 7. 开放问题

1. **镜像 Feistel 实现位置**：`flora-sanctum-core/crypto`（当前用途）还是 `flora-root`（通用原语）？
2. **迁移成本**：V1 兼容读保留多久？是否批量迁移旧密文到 V2（旧块 keyId 4 字节格式 → 重加密为 V2）？
3. **旧库补种**：无 repoKeyIdSeed 的仓库写入 V2 密文前，如何补种（VaultCreator 重建 / 迁移工具）？
4. **性能**：7 轮镜像 Feistel = 7 次 HMAC 调用（微秒级），索引 256→1 条/DEK（省内存）——收益/成本是否需要基准确认？
