# 方案：导入 KeePassXC（.kdbx）数据库

日期：2026-08-24
范围：flora-sanctum（密码管理器）新增「从 KeePassXC 导出文件导入」功能。

## 1. 目标与范围

让用户在 Sanctum 中通过「导入 → KeePassXC 数据库…」载入 `.kdbx` 文件，输入主密码（及可选的密钥文件）后，
把其中的分组与条目映射进当前打开的 Sanctum 仓库。

**首版范围（KDBX4-first）：**
- 支持 **KDBX 4.x**（KeePassXC 默认导出格式）。
- 主密钥：主密码（必选）+ 密钥文件（可选）。
- 解密后映射：分组、条目、标准字段、自定义字符串字段、TOTP、时间、图标（尽力映射）。

**首版明确不支持（后续扩展）：**
- KDBX 3.1（需 Salsa20 流密码，见 §7）。
- 附件 / 二进制（Binaries）、条目历史版本（History）、回收站。

## 2. 现有可复用资产

### 2.1 密码学原语（无需自研新原语，见结论）
| 需求 | 来源 |
|------|------|
| Argon2（KDF） | flora crypto `Argon2Kdf`（需扩展 **Argon2d** 变体，仅 type 参数之差） |
| AES-256-CBC（载荷加密） | JDK `Cipher("AES/CBC/PKCS5Padding")` |
| ChaCha20（载荷/受保护流密码备选） | JDK `Cipher("ChaCha20")` |
| AES-GCM（受保护流密码备选） | JDK `Cipher("AES/GCM/NoPadding")` |
| HMAC-SHA256 / SHA-256 | JDK `Mac` / `MessageDigest` |
| GZip 解压 | JDK `GZIPInputStream` |

> 原则说明：项目此前自研的是 JDK **缺失**的 GCM-SIV 与 Argon2id；AES-CBC / ChaCha20 / HMAC / SHA 均为 JDK 标准能力，
> 且不引入 BouncyCastle，符合「只自研 JDK 缺失部分」的既有约定。若项目实际禁用 `javax.crypto`、要求一律走 flora crypto，
> 则上述 4 项需改为自研——此前提需先确认（当前按「允许 JDK crypto」推进）。

### 2.2 数据模型（sanctum-core）
- `GroupNode`：可嵌套分组，自带 `folderDek`（`ObjectTree` 管理）。
- `EntryNode`：条目，预设字段由 `EntryFields.PRESET_NAMES = {password,url,username,labels,createTime,updateTime}` 确定性 uuid 写入；
  `name()` 为标题；自定义字段用 `createField()` 增加。
- `FieldNode`：`fieldName/value/kind`，`kind="totp"` 可生成验证码。
- `IconNode`/`IconTree`：图标以 name/data(Base64)/format 存储。

### 2.3 UI
- `SanctumGui` 已有弹窗、密码输入、分组树渲染等基础设施，导入流程复用即可。

## 3. KDBX4 解密流水线

```
读文件
  ├─ 校验 magic (0x9AA2D903 B54BFB67) 与版本号(4.x)
  ├─ 解析头部：变长字段 (ID:1B, Len:4B, Data)
  │     CipherID(2) / CompressionFlags(3) / MasterSeed(4) / EncryptionIV(7)
  │     KdfParameters(11) / PublicCustomData(12) / End(0)
  │     头部之后依次为 SHA256(头部, 32B) 与 HeaderHMAC(32B) 两条尾部字段
  ├─ 组装复合主密钥（KeePassXC CompositeKey 语义）
  │     rawKey 各分量 = SHA256(passwordUtf8) [+ SHA256(keyFile 内容)]   // 每分量先哈希
  │     composite    = SHA256( concat(各分量 rawKey) )                  // 复合键再哈希一次
  │     transformed  = KDF( composite , KdfParameters )                 // Argon2d，取原始输出（不再哈希）
  │     finalKey     = SHA256( MasterSeed ‖ transformed )
  ├─ 校验头部 HMAC（KeePassXC Kdbx4Reader 语义，已用真实文件核对）
  │     K_1     = SHA512( MasterSeed ‖ transformed ‖ 0x01 )
  │     hmacKey = SHA512( 8×0xFF ‖ K_1 )                              // getHmacKey(UINT64_MAX, K_1)
  │     computed = HMAC-SHA256( hmacKey , headerData(含 12B 魔数+版本+全部头部字段+End) )
  │     与存储 HeaderHMAC 比对 → 不一致即密码错误或文件损坏
  ├─ 解密载荷
  │     按 KDBX4 分块结构（每块带 HMAC）解出密文
  │     用 MasterCipher(finalKey) 解密：AES-256-CBC 或 ChaCha20
  │     CompressionFlags==GZip → GZIPInputStream 解压
  └─ 得到内层 XML（KeePass 文档格式）
```

**内层受保护字段（Protected="True"）：** 值以 Base64 存储，已与「内层随机流」异或。
`InnerRandomStreamKey` 由 finalKey 经 MasterCipher 解密得到 64 字节种子；按 KDBX4 规范用
ChaCha20（默认）或 AES-GCM 从该种子的指定偏移生成密钥流，对字段值异或还原明文。

> 头 HMAC 密钥、分块 HMAC、内层流偏移等字节级细节必须与真实文件逐步核对，
> 以 KeePassXC 生成的样本文件为基准测试向量（见 §6）。

## 4. 数据结构映射（KeePass → Sanctum）

| KeePassXC 元素 | Sanctum 映射 | 说明 |
|----------------|--------------|------|
| `Group`（含嵌套子 Group） | `GroupNode`（嵌套） | 1:1，直接建树 |
| `Entry` | `EntryNode` | 一个条目一个节点 |
| `String/Key=Title` | `EntryNode.name()` | 标题 |
| `String/Key=UserName` | 预设字段 `username` | `updateBuiltins` 写入 |
| `String/Key=Password`(Protected) | 预设字段 `password` | 受保护，先解密流再写入 |
| `String/Key=URL` | 预设字段 `url` | |
| `String/Key=Notes` | 自定义字段 `notes`（`createField`） | 无对应预设 |
| 其它 `String/Key=*` | 自定义字段 `createField(key)` | 保持原键名 |
| TOTP（字段如 `TOTP Seed` 或 otpauth URL） | `FieldNode(kind="totp")` | 命中即建 TOTP 字段 |
| `Times/CreationTime` | `createTime` | 解析 ISO 时间 |
| `Times/LastModificationTime` | `updateTime` | |
| `UUID`（16B） | **不保留**，Sanctum 生成新 UUID | 如需去重，可把原 UUID 存进某自定义字段备注 |
| 图标（数字索引 0–68） | **尽力映射** builtin 图标；否则无图标 | 见 §4.1 |
| `Binaries` / 附件 | 首版跳过 | |
| `History` 历史版本 | 首版跳过 | 仅导入当前版本 |
| 回收站 Group | 首版跳过 | |

### 4.1 图标弱映射
KeePass 用 0–68 的数字索引指代其内置图标，Sanctum 用命名 SVG 库，二者无权威对应表。
首版提供一张**小型人工对应表**（如 `0→key`、`1→web`、`19→lock`、`37→shield` 等少数常见项），
命中则设 `icon()`，未命中留空。后续可由用户手动改图标。

## 5. 模块与类设计（新增文件）

建议在 `flora-sanctum-app` 内新增包 `com.flora.sanctum.app.io.importer.kdbx`（外层 `io.importer` 放通用导入接口）：

- `KdbxFormatException`：解析/校验异常（密码错误、版本不支持等）。
- `KdbxHeader`：头部字段解析与校验（magic、版本、CipherID、压缩、MasterSeed、KdfParameters、HeaderHMAC）。
- `KdbxKey`：组装复合主密钥（密码 + 密钥文件），调用 `Argon2Kdf`（含 Argon2d 扩展）。
- `KdbxDecryptor`：头部 HMAC 校验 → 载荷分块解密 → 解压 → 内层 XML 字节。
- `KdbxXmlParser`：SAX/DOM 解析内层 XML，输出 `KdbxDocument`（分组树 + 条目 + 已解密的字段值）。
- `KdbxImporter`：编排上述步骤，把 `KdbxDocument` 映射写入当前 `ObjectTree`（建 GroupNode/EntryNode/FieldNode）。

UI 侧（`SanctumGui`）：
- 新增菜单项「导入 → KeePassXC 数据库…」。
- 文件选择（`.kdbx`）、主密码弹窗（含可选密钥文件）、导入进度与结果提示。
- 失败统一提示：「主密码错误」/「文件损坏或不支持的版本」。

> `Argon2Kdf`（flora crypto）需新增 `Argon2d` 支持：在现有实现上把 type 参数由 `1(id)` 扩为 `0(d)`。
> 该改动与导入功能同属一个主题提交。

## 6. 风险与验证策略

- **字节级细节是最大风险**：头部 HMAC 密钥派生、分块 HMAC、内层流偏移、KDF 参数读取，
  必须与真实文件逐字节核对。
- **验证方法**：用 KeePassXC 生成一个已知主密码、含若干分组/条目的测试库，
  以解密出的内层 XML 明文、若干受保护字段明文作为断言基准，逐步打通流水线。
- **Argon2d 参数**：memory/time/parallelism/盐从 `KdfParameters` 读取，与 KeePassXC 默认一致即可。
- **不写任何破坏现有仓库的改动**：导入只增不改，导入前可提示用户先备份。

## 7. 后续扩展（不在首版）

- **KDBX 3.1**：需新增 **Salsa20** 流密码（目前 flora crypto 没有），内层校验改为 SHA256(明文)。
- **附件/二进制**：若 `FieldNode` 支持二进制值，可建二进制字段；否则以 Base64 自定义字段兜底（体积大，谨慎）。
- **历史版本、回收站、图标完整映射表**：按需补充。

## 8. 结论

无需自研新的密码学原语（除把 `Argon2Kdf` 扩出 Argon2d 变体）。工作量集中在
**KDBX4 格式/协议解析**与**数据结构映射**两块，均有现成模型与 JDK 原语兜底，风险可控。
建议按本方案先落地 KDBX4 首版，再用真实文件驱动验证。
