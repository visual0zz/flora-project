# 决策：块信封去内嵌 uuid，root 改 KEK 直加密 + uuid 由 KEK 单向推导

日期：2026-08-31
模块：flora-sanctum-core（store / crypto / model）

## 背景

原信封头内嵌 16 字节对象 uuid（`magic+version+flags+uuid(16)+nonce+keyId`），
而块文件路径本身已由 uuid 派生（`{a}/{b}/{rest}.md`）——即同一身份存了两份（块内一次、路径一次）。
此外 manifest 明文块还记录 `rootObjectUuid`，根对象内部再持一份"KEK 包裹的 root DEK"（内嵌 blob）。

## 候选方案

- 候选 A：维持现状（uuid 既入块头又入路径）。
- 候选 B：块头仍存 uuid，仅把 AAD 顺序改为 `uuid ‖ 时间戳 ‖ 头`。
- 候选 C（最终采用）：uuid **完全不写入块内**，由文件路径反推并进 AAD；
  root 对象直接用 KEK 加解密（去除内嵌包裹 DEK），其 uuid 由 KEK 单向推导，manifest 不再记录。
- 候选 D：root uuid 用带密钥的可逆函数（对合）推导。→ 用户复议后改为单向。

## 决策

采用候选 C；root uuid 的推导采用**单向** keyed 函数（不用可逆对合）。版本号保持不变。

**Why：**

1. **消除身份冗余与不一致风险**：uuid 存两份意味着两者可能不一致；去掉块内那份后，
   路径成为身份的唯一来源，块不再"自述"身份。
2. **等价且更强的安全绑定**：uuid 仍参与 AAD（`uuid ‖ 时间戳 ‖ 信封头`），
   因 uuid 来自路径，**块被搬到别的路径即反推出不同 uuid → AAD 不一致 → 解密失败**，
   即免费获得"防 relocation"；同时每块省 16B。
3. **root 去内嵌 blob**：根对象原本"用随机 root DEK 加密 + 把 KEK 包裹的 root DEK 内嵌进自身 JSON"
   是多余的间接层；直接用 KEK 加解密后结构更短、无内嵌 blob。
4. **root uuid 用单向推导**：候选 D 的对合可逆在本场景无用（只需"给同一 KEK 得同一 uuid"），
   而单向（HMAC-SHA256）更强：拿到根对象块无法反推主密钥，也无法离线枚举某密码对应的根对象位置。
5. **不升版本号**：当前未投产、无存量数据需兼容，故信封 `version` 字节仍为 `VERSION_1`(=1)，
   格式直接切换、旧数据作废。

## 影响与连带变更

- `HEADER_LEN` 44→28、`PLAINTEXT_HEADER_LEN` 24→8；`nonceOff` 由 `MAGIC_LEN+2+16` 改为 `MAGIC_LEN+2`。
- `CipherCodec.decode` 需由调用方传入 uuid（文件块取自路径，内嵌块用 `CipherCodec.EMBEDDED_UUID`），
  不再从块内读出；`DecodedBlock`/`writeUuid`/`readUuid`/`BlockHeader.uuid` 删除。
- `Block` 的 uuid 由 `MarkdownObjectStore` 在构造时从路径反推并注入；路径不符合分片布局的块在扫描时跳过
  （异常落在 `scanFile` 已有的 `catch (Exception ignore)` 内，不会打断整次扫描）。
- **密码轮换语义变化（重要）**：根级密钥即 KEK、无独立 root DEK 可"只换包裹"，且 root uuid 随 KEK 变化，
  故换主密码时：根对象改写至新路径、旧路径删除；所有 parent 指向根对象的根级块改指新 root uuid 并以新 KEK
  重新加密；根级分组 DEK 改用新 KEK 包裹。更深层级（以分组 DEK 加解密、parent 为分组 uuid）不受影响。
  这与旧设计"只重包 root DEK、子链不动"不同，轮换成本从 O(1) 变为 O(根级块数)。
- manifest JSON 不再含 `rootObjectUuid`；其 MAC 输入与密文统一为 `uuid ‖ 时间戳 ‖ 头 ‖ 负载` 顺序。

## 验证

- `flora-sanctum` 全模块单测通过（core 74 + kdbx 18 + vault-formats 7 + app 17）。
- 新增用例：`blockOmitsUuidHeaderField`（uuid 字节不出现在块内）、`decodeFailsOnWrongUuid`（换 uuid 即失败）、
  `relocatedBlockFailsAuthentication`（改块文件名后该块无法解密）、
  `changeMasterPasswordMigratesRootLevelGroupDek`（轮换后顶层分组 DEK 可解、分组内条目可读）、
  `unlockReportsMissingRootObject`（根对象缺失报 ROOT_MISSING）。
