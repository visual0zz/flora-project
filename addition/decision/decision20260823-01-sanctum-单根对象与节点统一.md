# decision: 单根对象与节点结构统一（2026-08-23）

> 涉及 flora-sanctum 存储模型统一重构：多根概念 → 单根对象；remote 独立 type；字段名统一；config 扁平。

## 背景

原模型存在多处不一致：

1. **parent 指向两套表达**：顶层 group/entry/config/remote 指向根概念 tag 字符串（`"data"`/`"remote"`），而 icon/sshKey 指向 root group 的 uuid。
2. **remote 复用 field 存储**：`type=field, kind=remote`，与预设字段 `type=field` 同 type 不同义。
3. **字段家族三种 type**：预设字段 `field`、自定义字段 `customField`、远程 `field+kind=remote`。
4. **名称字段不统一**：group/entry/sshKey 用 `name`；field/customField/remote 用 `fieldName`；config 用 `key`。
5. **value 形态不统一**：config/field 的 value 是字符串；remote 的 value 是对象 `{url,keyRef}`。
6. **icon/sshKey 顶层节点无法被 `roots()` 匹配**（parent 是 uuid 而非 tag），只能靠全扫描。

## 决策 1：单根对象模型

- **RootTag 缩减为 MANIFEST、DATA 两个**：manifest 独立引导块（解锁必须先读，parent 保持 `"manifest"`）；data 是仓库唯一根对象（type=root，parent=`"data"`，持 root DEK + repoKeyIdSeed）。
- **所有顶层节点 parent 统一指向根对象 uuid**：group/entry/config/icon/sshKey/remote 顶层一律 parent=根对象 uuid。
- **DataTree 分类改用 NodeType**（原 RootTag 已不足以区分四棵树）：对象树=GROUP、图标=ICON、SSH=SSH_KEY、远程=REMOTE。
- **DEK 统一为 data 根 DEK**：不再有 icon/sshKey 独立 root DEK；所有节点用唯一根 DEK 加密。文件夹 DEK 链保留（文件夹级隔离）。
- 删除 ICON/SSH_KEY/REMOTE root group 的创建；VaultUnlocker 只发现一个根对象。

**Why**: 存储结构统一、parent 语义单一、GC/解锁路径简化（单根即根集合）；用户明确"整个仓库只有一个根对象"。
**How to apply**: `RootTag`/`DataTree`/`VaultCreator`/`VaultUnlocker`/`MasterKeyRotator` 相应调整；`roots()` 按根对象 uuid 匹配。

## 决策 2：remote 独立 type + 扁平结构

- remote 落盘 `type=remote`（不再复用 field+kind），`belongsTo` 只认 type。
- 去掉 value 层，直接存 `name` / `url` / `keyRef`。
- `FieldKind.REMOTE` 移除。

**Why**: 语义类型=存储类型，消除 field 家族歧义；扁平化免去 value 解构。
**How to apply**: `RemoteTree`/`RemoteNode`/`LibraryConfig.remotes()` 调整。

## 决策 3：字段名统一为 name

- field/customField 的 `fieldName` → `name`；config 的 `key` → `name`。
- group/entry/sshKey/remote 统一用 `name`。
- `FieldNode.fieldName()` Java 方法名保留（读取 `name` 键），避免扩散改动。

**Why**: 名称字段单一来源，跨节点类型一致。
**How to apply**: 创建/读取路径全部改 `name`；外部密钥字段（externalKey）同步。

## 决策 4：manifest 记录根对象 uuid（解锁 O(1) 定位）

- manifest 明文新增 `rootGroupUuid`（根对象 uuid，非机密），写入 MAC 覆盖范围。
- VaultCreator 先定根对象 uuid 再写 manifest 与根对象块。
- VaultUnlocker 按 manifest 定位根对象块，KEK 试解出 root DEK 与 repoKeyIdSeed，不再全库扫描试解。
- GarbageCollector 根集合 = manifest 明文块 + manifest 记录的根对象 uuid，无需 KEK 试解兜底。

**Why**: 根对象位置由引导块明确记录，解锁/GC 免全库扫描（原需遍历每块 KEK 试解）；根对象 uuid 非机密，明文记录无泄密风险。
**How to apply**: `Manifest`/`VaultCreator`/`VaultUnlocker`/`GarbageCollector`/`ManifestStore`/`MasterKeyRotator` 相应调整。

## 决策 5：去除普通节点 version 字段

- 普通节点（root/group/entry/field/customField/config/icon/sshKey/remote）不再写 `version`。
- 仅 manifest 保留 `version`（格式版本由块头 Envelope.VERSION_2 + manifest.cryptoVersion 共同表达）。

**Why**: 普通节点 version 写入后从不读取，是纯冗余；格式演进有块头版本与 manifest 版本兜底。
**How to apply**: 各创建路径删除 `version:1` 写入。

## 决策 6：icon 增加 name

- icon 节点新增可选 `name`（导入时取文件名），`IconTree.createIcon(name, data, format)` 带名称。
- GUI 图标列表优先显示 name，缺失时回退"图标 [format]"。

**Why**: 原 icon 无名称，多图标时无法区分。
**How to apply**: `IconTree`/`IconNode`/`SanctumGui.doImportImage`/`iconLabel` 调整。

## 决策 7：manifest / 根对象去掉 parent 字段

- manifest 不再写 `parent`（识别靠 type=manifest + 明文块；MAC canonical 同步去除该段）。
- 根对象不再写 `parent`（manifest 已记录 rootGroupUuid O(1) 定位，唯一根即 DATA）。
- VaultUnlocker 按 manifest 定位根对象后直接登记 DATA，不再 `RootTag.fromTag(parent)`。
- GarbageCollector 根集合 = manifest 明文块 + manifest 记录的根对象 uuid，删除 `RootTag.isRoot(parent)` 死分支。
- `RootTag` 缩减为仅 DATA（MANIFEST 不再需要）。

**Why**: 两个 parent 字段无逻辑消费方（manifest 识别不靠 parent；根对象定位靠 manifest.rootGroupUuid），是恒值自覆盖冗余。
**How to apply**: `Manifest`/`VaultCreator`/`ManifestStore`/`MasterKeyRotator`/`VaultUnlocker`/`GarbageCollector`/`RootTag` 调整。

## 决策 8：manifest 块格式与密文对齐

- manifest 明文块从 `header + JSON(含 mac 字段)` 改为 **`header + JSON 负载 + MAC(尾附)`**。
- MAC = HMAC-SHA256(macKey, **完整信封头** ‖ **时间戳** ‖ JSON 负载)，不再覆盖"仅 uuid + 负载"。
- MAC 不再存于 JSON 内部，尾附于负载之后（32 字节，位置对应密文 tag）。
- `Manifest` 类去掉 mac 字段/canonical/computeMac；块构造/解析/MAC 计算收敛到 `ManifestStore`（静态工具）。
- `BlockFormat` 新增 `MANIFEST_MAC_LEN = 32`。

**Why**: 与密文 AAD（完整信封头+时间戳）思路一致，认证覆盖面更完整；mac 尾附使负载 JSON 不再携带认证值（结构统一、可读性更好）。
**How to apply**: `Manifest`/`ManifestStore`/`VaultCreator`/`VaultUnlocker`/`MasterKeyRotator`/`BlockFormat` 调整；`VaultUnlockerTest` 手写块同步。

## 决策 9：校验数据排序与 version 统一

- **AAD/MAC 输入排序**：从 `信封头 ‖ 时间戳` 改为 **时间戳 ‖ 信封头**（manifest 为 时间戳 ‖ 头 ‖ 负载），与文件前缀 `timestamp:base58` 在前的读取顺序一致。
- **version 统一为 1**：密文块 version 从 2 改为 1，删除 `VERSION_2`；块类型完全由 flags 区分（FLAG_CIPHER 0x01 / FLAG_PLAINTEXT 0x02），version 字段只表达格式版本。

**Why**: 排序与读取顺序直觉一致；version 不再承担"明文/密文"双重角色（块类型本就由 flags 表达）。
**How to apply**: `ManifestStore.macInput`/`CipherCodec`（encode/decode 的 AAD 拼接与 version 检查）调整；`BlockFormat`/`Envelope` 删除 VERSION_2。

## 决策 10：清理冗余代码

- 删除无调用方组件：`CodecRegistry`、`RawCodec`、`ArchiveExporter`（+`Sanctum.exportArchive`）、`Metadata`（+`Sanctum.metadata`）。
- 删除无调用方法：`VaultUnlocker.registerDek`、`Block.keyId`/`Block.base58`（字段+构造参数）、`BlockHeader.keyId`、`Blake2bDigest.of256`、`TreeNode.exists`、`Vault.rootDek`/`Vault.rootDeks`、`Sanctum.objectCount`。
- `Sanctum` 移除 `manifestStore` 字段（原为 metadata 定位专用）。
- `BlockHeader.keyId` 原读 4 字节而 `KEYID_LEN=8`——死代码中的隐藏 bug，随删除一并消除。
- 测试 `nodeAndMetadataCarryBlockLocation` 改为 `nodeAndManifestCarryBlockLocation`（经 ManifestStore 验证 manifest 块定位）。

**Why**: 均为无外部消费的死代码，删除简化模型并消除隐藏 bug。
**How to apply**: 删除文件/方法后全量编译测试验证无残留引用。

## 验证

- core 55 + app 10 用例全绿；含新增 `rootParentsUseRootUuid`（顶层 parent 均等于根对象 uuid）、GC 存活保留、解锁后 remote/icon/sshKey 可读、icon name 往返、manifest 记录 rootGroupUuid、manifest 尾附 MAC 认证、AAD/MAC 排序与 version 统一。
- `MarkdownObjectStoreTest.uuidPrefixUniform` 为随机分布测试，偶发波动（与本次改动无关）。
