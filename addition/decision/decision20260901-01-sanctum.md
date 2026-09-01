# decision20260901-01-sanctum

**决策**：去掉 DEK 内层包裹（wrapDek），组/根对象 JSON 的 `dek` 字段直接存明文 DEK（base64）。

**模块**：flora-sanctum-core（存储/加密格式）

**日期**：2026-09-01

## 背景

组对象块的整体加密（`CipherCodec`）用父 DEK 以 AES-GCM-SIV 加密整个 JSON
（`AAD = uuid ‖ timestamp ‖ 信封头`，GCM tag 认证全部负载），已完整覆盖 JSON 中的
`type`/`name`/`parent`/`dek` 所有字段。在此基础上，内层 `wrapDek`（用**同一个**父 DEK
对子 DEK 单独加密成内嵌信封，再 Base64 放入 `dek` 字段）在安全强度上与「明文 DEK 直接入
JSON」完全等价：攻击者解出子 DEK 都只需要父 DEK（解外层块 → 取字段 → 解内层，两次都用
同一父 DEK），且外层 tag 已认证整个 JSON，内层 tag 不提供额外防篡改。

## 决策

去掉内层包裹：

- 组对象 JSON 的 `dek` 字段：`Base64(明文子 DEK)`（原为 `Base64(父DEK包裹密文信封)`）；
- 根对象 JSON 的 `dek` 字段：`Base64(明文 rootDek)`（原为 `Base64(KEK包裹密文信封)`）；
- 删除 `TreeContext.wrapDek` 与 `VaultUnlocker.unwrap`；
- `NodeMover` / `MasterKeyRotator` 不再对 `dek` 字段重新包裹，仅把组块/根块整体改用新父 DEK 重加密；
- `CipherCodec.EMBEDDED_UUID` 保留（`ExternalKeyService` 仍用于无文件路径的外部密钥 blob）。

安全语义不变：组块/根块外层整体加密提供等价机密性与完整性。

## 理由

- 内层 wrap 在「外层同一父 DEK 整体加密」设计下不增加安全强度（见背景）；
- 简化实现：去掉一层 CipherCodec 信封、`EMBEDDED_UUID` 约定、以及创建/解锁/移动/换主密码
  四处对 `dek` 字段的包裹与解包逻辑。

## 代价 / 兼容性

- **格式变更**：旧库（`dek` 字段为密文信封 base64）在新代码下按明文解码会得到错误的 DEK，
  数据块将解不开，库整体落入「不可解锁」。
- 项目尚未发布正式数据，接受此不兼容变更，不做旧格式读兼容。
