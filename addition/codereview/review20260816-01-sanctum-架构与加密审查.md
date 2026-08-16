# review20260816-01-sanctum 架构与加密审查

> 范围：flora-sanctum-core（crypto/store/model）+ flora-sanctum-app（sync/server）当前实现
> 重点：代码结构合理性、职责分配合理性、加密算法正确性、保护强度
> 审查基于重构后（树化 + core/app 分工）的代码快照
> **修复状态**：本报告列出的 P2 项与大部分 P3 项已修复（见 §5"修复结果"），全量测试通过。

## 1. 代码结构合理性

### 结论：总体合理，遗留 ExternalKeyService 一处不一致

**良好面**
- core/app 职责分离清晰：core 只做数据读写（crypto/store/model/config），sync/server 在 app，无反向依赖。
- model 树化到位：`Sanctum = Metadata + LibraryConfig + List<DataTree>`，节点（GroupNode/EntryNode/FieldNode 等）承载操作，`Sanctum` 仅门面。
- 上帝类拆分到位：ManifestStore/GarbageCollector/MasterKeyRotator/ArchiveExporter/TreeContext 独立。
- 接口类型化：节点返回类型化值，app 不再接触 `JsonObject`。

**问题**
- [P2] `store` 与 `crypto.impl.Envelope` 常量重复：`BlockFormat`（store）与 `Envelope`（crypto.impl）各持一份 magic/偏移常量，需人工同步。一旦只改一处即静默错乱。建议 `Envelope` 改为引用/继承 `BlockFormat`，单点定义。
- [P3] `ObjectStore`/`Codec` 接口注释自称"内部，不对外暴露"，但 `module-info` exports `com.flora.sanctum.store`（app 已用 `BlockFormat`/`BlockHeader`）。导出面名不副实，可收紧为仅公开工具（或调整注释）。
- [P3] `Sanctum.store()` 包私有 + `Sanctum.vault()` 公开被 `ExternalKeyService` 直接使用（绕过树），详见 §2。

## 2. 职责分配合理性

### 结论：大部合理，`ExternalKeyService` 是重构后遗留的不一致点

**问题**
- [P2] **ExternalKeyService 绕过树/节点 API**：`createExternalKey` 直接 `store().put(uuid, block, RawCodec)` 写块，绕过 `TreeContext`/`EntryNode.createField`，与"节点负责操作"的新架构不一致。字段块的加密（data DEK）与时间戳由它自己处理，与节点写路径重复。建议改用 `EntryNode.createField(fieldName, value, "externalKey")`。
- [P2] **createExternalKey 用墙钟时间写 updateTimestamp**（`System.currentTimeMillis()`），违反设计 02"updateTimestamp 走仓库时间戳"约定（其他所有写入都用 `nextTimestamp()`）。应改走 TreeContext 时间戳。
- [P3] **ExternalKeyService.list/decrypt 每次 `store.scan()` 全库扫描**（O(全库块)），大库下外部密钥加解密是线性扫描；可基于 TreeContext 内存对象图过滤，避免重复解密。
- [P3] `Sanctum` 门面仍暴露 `vault()`（内部密钥状态给 app/GUI 直接访问 rootGroupUuid 等）——重构后 app 需要 `tree(tag)` 定位 root，`vault()` 可降为包私有或提供窄接口（如 `folderDek`）。当前 `SanctumGui` 已改用树 API，`vault()` 仅测试/内部使用，可收紧。

## 3. 加密算法正确性

### 3.1 Argon2id（主密码 → KEK）— 正确
- 默认 256 MiB / 3 迭代 / 4 并行：高于 OWASP 推荐（≥128 MiB），合理高安全档；参数入 manifest 可升级。
- salt 16 字节随机；`memoryKiB < 8*parallelism` 有下限校验。
- 密码转 UTF-8 正确处理代理对；临时字节在 finally 清零。
- **注意**：设计"salt 终身不变"，换主密码复用 salt。Argon2 对固定 salt 的暴力破解成本不变（每个候选密码仍一次全内存计算），安全可接受；但若有"多主密码可离线对比"担忧，换 salt 需重加密全部（成本高），当前取舍合理。

### 3.2 HKDF-SHA256（RFC 5869）— 正确
- extract/expand 实现符合规范；expand 长度上限 255*32 校验；counter 从 1 开始。
- 用于 encKey（`"sanctum-enc"`）、manifest MAC 密钥（`"sanctum-manifest-mac"`）、熵混合，info 域分离，无跨用途复用。

### 3.3 AES-256-GCM-SIV（BC 原生接口）— 正确
- `CipherCodec.encode/decode` 对称：AAD = 整个信封头（magic‖version‖flags‖uuid‖keyId‖nonce），tag 覆盖密文+头，防篡改/搬运/降级。
- nonce 96 位 CSPRNG 随机；GCM-SIV 抗 nonce 误用（RFC 8452 特性），碰撞仅泄露"同明文同密文"相等性。
- 随机异或混淆：xorByte 不落盘，从 `bytes[0]⊕magic[0]` 反推，落盘字节全随机化（抗指纹/反聚类）。加密在混淆之前用真实值，逻辑正确。

### 3.4 块信封偏移 — 有一处硬编码残留
- [P2] **`ExternalKeyService.decrypt` 仍硬编码偏移**：
  - `block.length < 26`（应为 `BlockFormat.MAGIC_LEN+2+16+4 = 30`）
  - `System.arraycopy(block, 22, keyId, 0, 4)`（keyId 偏移应为 `BlockFormat.MAGIC_LEN+2+16 = 26`）
  - **现状功能未受影响**：读出的 `keyId` 变量实际未被使用（解密靠遍历 externalKey 候选 + tag 试解），`length<26` 校验宽松于真实最小长度。
  - 但这是魔数扩展（4→8 字节）时的遗漏：与 `BlockFormat`/`Envelope` 不一致，未来信封格式再次变化时此处会静默失效。应改用 `BlockFormat` 常量并删除未使用的 `keyId` 读取（死代码）。
- 其余偏移（CipherCodec/BlockResolver/BlockHeader/Block/ManifestStore/SyncService）均已用常量，正确。

### 3.5 keyId 索引定位 — 正确
- 每 DEK 预计算 256 个 keyId（byte1 + SHA256(DEK‖byte1)[0:3]），查表候选集 + GCM-SIV tag 试解确证。
- 32 bit keyId（24 bit 哈希 + 8 bit 随机 byte1）→ 跨 DEK 碰撞 ~1/65536，命中格候选通常 1 个。
- `clear()` 擦除内存 DEK 副本。

### 3.6 TOTP（RFC 6238）— 正确
- HMAC-SHA1 + 动态截断（RFC 4226），counter = unixSeconds/period，6/8 位；实现标准。

### 3.7 外部密钥（kind=externalKey）— 正确但有隐患
- 加密用字段独立密钥材料经 HKDF 派生 encKey + GCM-SIV；解密候选域仅限 externalKey 字段（系统 DEK 不在候选），防泄漏设计正确。
- 隐患见 3.4（偏移硬编码）与 §2（绕过树/时间戳）。

### 3.8 manifest MAC — 正确
- MAC = HMAC-SHA256(HKDF(KEK,"sanctum-manifest-mac")，canonical)，canonical 覆盖信封头 uuid + 全部负载字段（含 parent），防篡改/KDF 降级/参数降级。
- 换主密码后以新 KEK 重算 MAC，流程正确。

## 4. 保护强度评估

| 面 | 评估 |
|---|---|
| 主密码 KDF | Argon2id 256MiB/3/4（强），参数随 manifest 可升级 |
| 密钥层次 | KEK→3×root DEK→folder DEK 链式包裹，DEK 非 root 落盘（密文），层次合理 |
| 数据加密 | AES-256-GCM-SIV（抗 nonce 误用）+ 全量认证 + 随机异或混淆 |
| 目录/搜索 | 对象平铺随机 UUID，无明文结构暴露（组数/形状/字段数不可见） |
| 防篡改 | manifest MAC + 每块 tag；GC 软删除可审查 |
| 内存清除 | lock/clearSecrets 擦除 KEK/DEK/keyId 索引；密码临时数组清零 |
| 威胁模型 | 不防整体回滚（git 历史/远端背书）；外部密钥候选域限定 |

**强度结论：对"静态库文件失窃"与"解锁后进程内泄露"两个主威胁有充分保护。** 主密码 Argon2id 参数强、AEAD 选型抗误用、密钥分层与内存清除到位。

**可强化项（非必须）**
- [P3] 解锁后进程内密钥（KEK/DEK）驻留内存——即使 clearSecrets 后 JVM 堆可能残留拷贝，属 Java 固有限制；如需更强可引入 native 密钥存储（权衡复杂度）。
- [P3] salt 终身不变：若未来需要支持"更换主密码同时更换 salt"的场景，需设计全库重加密迁移路径（当前只重包 root DEK）。
- [P3] 剪贴板定时清空已实现；可考虑系统剪贴板历史（macOS/Windows 会记录）提示用户。

## 5. 修复结果（已按本报告处理）

1. **[P2 已修复] ExternalKeyService.decrypt**：改用 `BlockFormat.MAGIC_LEN+2+16` 读 keyId、`BlockFormat` 校验长度；并改为**keyId 索引定位**（新增懒构建的 `KeyIdIndex`，externalKey 密钥材料 register 进索引，decrypt 用块头 keyId 查表定位候选再 tag 试解，不再遍历全部 externalKey 密钥）。
2. **[P2 已修复] ExternalKeyService.createExternalKey**：updateTimestamp 走仓库时间戳（`TreeContext.nextTimestamp()`）；字段写入改用 `TreeContext.writeWithDek`（不再直接 `store.put + RawCodec`）；新密钥即时 register 进索引。
3. **[P2 已修复] Envelope 与 BlockFormat 合并**：`crypto.impl.Envelope` 改为转发 `store.BlockFormat` 常量，单一事实来源。
4. **[P3 已修复] ExternalKeyService 列表/解密遍历**：解密经 keyId 索引定位；列表仍按需扫描（O(n) 但仅列表场景）。
5. **[P3 已修复] Sanctum.vault() 收紧**：`vault()`/`folderDek()` 降为包私有（model 包内部使用），app 经数据树访问，不再暴露密钥状态。
6. **[P3 保留] store 导出面**：`module-info` 保持导出 `com.flora.sanctum.store`（app 的 SyncService 需要 `BlockFormat`/`BlockHeader`）；仅更新注释说明其为存储层公开 API。

**验证**：core 43 项 + app 4 项测试全部通过（含 ExternalKeyService 加解密往返、HTTP 外部密钥服务）。

## 6. 关联文件
- flora-sanctum-core/.../model/ExternalKeyService.java（§2/§3.4/§5 主要修复）
- flora-sanctum-core/.../store/BlockFormat.java 与 crypto/impl/Envelope.java（常量合并）
- flora-sanctum-core/.../model/{TreeContext, Sanctum}.java（职责/门面）
- flora-sanctum-core/src/main/java/module-info.java（导出面说明）
