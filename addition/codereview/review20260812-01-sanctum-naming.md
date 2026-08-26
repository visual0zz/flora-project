# 命名审查：flora-sanctum 模块

- 审查范围：`flora-sanctum-core` 与 `flora-sanctum-app` 的 `src/main` Java 源码（不含测试）。
- 审查日期：2026-08-12
- 目标：类/接口、字段、方法、枚举常量与局部变量命名中的风格不一致、误导性、含义不清问题。

> 说明：本次审查已对两轮初步结论做了二次核验。其中「`keyId`/`dekId` 角色反转」一项经核对
> `KeyIdDeriver` 的 Javadoc（`dekId`=内部索引键、`keyId`=密文头写入值）自洽且语义明确，
> 不属于命名问题，已从报告中剔除。

---

## 高优先级（导出 API / 明确误导）

### H1. `rootGroupUuid()` 实为「根对象 uuid」，名字误导
- 位置：
  - `model/Sanctum.java:92` — `public UUID rootGroupUuid()`，其 Javadoc 写的是「仓库唯一根对象 uuid」。
  - `model/vault/Manifest.java:69` — `rootGroupUuid()`。
  - `model/vault/Vault.java:65` — `rootGroupUuid(RootTag tag)`。
  - 调用处注释亦称其为「根对象」（如 `VaultUnlocker.java:53,61`：`manifest.rootGroupUuid` 定位根对象）。
- 问题：方法名含 `Group`，但术语体系中它指向的是 `RootTag.DATA` 的**根对象**（`type=root`），并非
  `NodeType.GROUP`（分组文件夹）。导出 API，外部调用方极易误认为返回某个分组节点。
- 建议：统一改名为 `rootObjectUuid()` / `rootObjectUuid(RootTag)`，并同步 `Manifest` 字段
  `rootGroupUuid`（L23/36/69/90）与 JSON key `rootGroupUuid`（如愿意可一并改 schema，否则至少改名方法）。
- 影响面：方法 + 内部字段 + JSON 持久化 key，需整体替换。

### H2. `Argon2Kdf` 缩写大小写不一致（`Kdf` 应为 `KDF`）
- 位置：`crypto/Argon2Kdf.java:12`；所在包 `com.flora.sanctum.crypto` 经 `module-info.java:10` 导出。
- 问题：项目约定缩写全大写（`HkdfSha256`、`GcmSiv`、`HmacSha1`、常量 `KDBX`/`MAC`/`DEK`/`KEK`），
  `Kdf` 是唯一骆驼式缩写，且泄漏到导出 API。
- 建议：改名为 `Argon2KDF`（含所有引用点、导出、测试）。

### H3. `ExternalKeyService.KeyInfo` 命名含糊且为可变公共字段类
- 位置：`model/ExternalKeyService.java:168` — `public static final class KeyInfo { public final UUID uuid; public final String name; public final String description; }`。
- 问题：
  1. `KeyInfo` 易被误解为「键盘信息 / key 的通用信息」；作为「外部密钥」的元信息，应显式命名。
  2. 公共 API 返回类型却暴露裸 `public final` 字段，与同模块 `LibraryConfig.RemoteConfig`（record）风格不一致。
- 建议：改为 `record ExternalKeyInfo(UUID uuid, String name, String description)`。

### H4. `VaultForm` 类名误导（非 UI 表单）
- 位置：`app/bootstrap/VaultForm.java:23`。
- 问题：类名 `Form` 像 Swing 表单，实际是 vault 形态检测器/配置（`detect`、`dataDir`、`loadRepoConfig`、`vaultRoot`）。与新人对接时极易误解为 UI 控件。
- 建议：`VaultFormFactor` / `VaultShape` / `VaultLayout` / `VaultDetector`。

### H5. `ImportException.isAuthFailure()` 基于消息字符串匹配，契约脆弱且名不副实
- 位置：`app/io/importer/ImportException.java:18-21`。
- 问题：方法名暗示「鉴权失败分类」，实则是匹配异常消息中的子串「主密码」「密钥」。语言改变或文案调整即失效，外部调用方无法信任其语义。
- 建议：引入显式错误类别字段/枚举替代字符串匹配；短期内至少改名 `guessedAuthFailureFromMessage()` 以反映实现本质。

---

## 中优先级（同模块内不一致 / 局部歧义）

### M1. `lastModTime` 与 `creationTime` 缩写风格不一致
- 位置：`app/io/importer/kdbx/KdbxDocument.java:59-60` — `public Long creationTime;` / `public Long lastModTime;`。
- 建议：`lastModificationTime`（与 `creationTime` 对齐）。

### M2. `EntryNode.icon()` / `GroupNode.icon()` 返回字符串引用，与 `IconNode.iconData()` 混淆
- 位置：
  - `model/tree/EntryNode.java:56` — `public String icon()`。
  - `model/tree/GroupNode.java:33` — `public String icon()`。
  - `model/tree/IconNode.java:30` — `public byte[] iconData()`（返回字节数据）。
- 问题：同为「icon」，`icon()` 返回 uuid/「builtin:name」字符串引用，`iconData()` 返回图标字节，含义不同却同名前缀。
- 建议：引用型改名 `iconRef()` / `iconReference()`，明确区分于 `iconData()`。

### M3. ChaCha20 / SVG 等缩写在常量与文档间大小写不统一
- 位置：
  - `app/io/importer/kdbx/KdbxCipher.java:20,22` 常量 `AES` / `CHACHA20`，而同模块 `KdbxStreamCipher`、`Salsa20`、`KdbxParser` 使用混合大小写「ChaCha20」。
  - `app/ui/SvgIcon.java` 类用 `Svg`（无元音），注释/Javadoc 中「SVG」「svg」混用（L33/147/173）。
- 建议：算法名统一为 `CHACHA20`（大写常量）+ 调用方引用常量而非字面串；SVG 在注释中统一大写 `SVG`，类名保留 `SvgIcon`（Java 风格）。

### M4. `SecureRandomSource` 命名问题
- 位置：`crypto/impl/SecureRandomSource.java`：
  - L23 `private long pool;`：注释称「熵累积器状态」，但它是单个 `long` 抖动累加值，非「池」。
  - L36 `byte[] p` / L39 `byte[] o`：单字母，含义不清（primary / overlay）。
- 建议：`pool` → `jitterAccumulator`（或 `entropyAccumulator`）；`p`/`o` → `primaryBytes`/`overlayBytes`。

### M5. `Manifest` / `VaultCreator` 持久化参数 `m/i/p` 含义不清
- 位置：
  - `model/vault/Manifest.java:96-98` — `params.getInt("m"/"i"/"p")`。
  - `model/vault/VaultCreator.java:62` — `writeManifestBlock(byte[] salt, int m, int i, int p, ...)`。
  - 镜像点：`ManifestStore.write`、`VaultCreator.writeManifestBlock` 写入 `"m"/"i"/"p"`。
- 问题：参数名 `m/i/p` 与持久化 JSON key 同样晦涩（memory/iterations/parallelism）。
- 建议：形参 `memKiB`/`iterations`/`parallelism`，并在注释中说明持久化 key 缩写含义。

### M6. `SanctumGui` 多个字段含义不清 / 同名类型双字段
- 位置：`app/ui/SanctumGui.java`：
  - L77 `private final AtomicReference<Sanctum> current`：与同类型字段 `sanctum`（L81）并存，二者角色不清。
  - L100 `openVaultPath`：实为「当前已解锁 vault 路径」，命名像「打开」动作参数。
  - L104 `pendingRoot` / L106 `pendingIsNew`：「pending」语义笼统，实为解锁页要展示的 vault 根 / 是否「新建」模式。
- 建议：`current` → `currentSanctum`（并清理冗余 `sanctum`）；`openVaultPath` → `currentVaultPath`/`unlockedVaultPath`；`pendingRoot` → `targetVaultRoot`，`pendingIsNew` → `unlockIsCreate`/`unlockMode`。

### M7. `SvgIcon.LIBRARY` 字段过于泛化
- 位置：`app/ui/SvgIcon.java:66` `LIBRARY`（`List<String>` 图标名缓存）与 `LIBRARY_PREFIX`（L? 路径前缀）并存，易混「图标库」与「文件路径前缀」。
- 建议：字段 `cachedIconNames`；常量 `LIBRARY_PREFIX` → `ICON_LIBRARY_PATH_PREFIX`。

### M8. `KdbxParser.FF8` 名称晦涩
- 位置：`app/io/importer/kdbx/KdbxParser.java:32` — `FF8`（8 字节 0xFF 填充值）。
- 建议：`FILL_BYTES` / `HMAC_KEY_FILL_BYTES`（并保留用途注释：KDBX HMAC key 派生填充值）。

### M9. `KdbxStreamCipher.type` 字段名过泛
- 位置：`app/io/importer/kdbx/KdbxStreamCipher.java:28` — `private final int type;`（内层流算法 id 2=Salsa20/3=ChaCha20）。
- 建议：`innerStreamId`（与构造参数、KdbxXml 的 `innerStreamId` 对齐）。

### M10. `ImportContext` 在 `KdbxMapper` 中被命名为 `ctx`
- 位置：`app/io/importer/kdbx/KdbxMapper.java:21` — `private final ImportContext ctx;`：以类型缩写命名，且 `ctx` 过于泛化。
- 建议：`importContext` / `context`。

### M11. `Argon2` 中死常量 `ARGON2id`
- 位置：`crypto/impl/Argon2.java:13` — `private static final int ARGON2id = 2;` 从未使用（真实类型常量为 `TYPE_D`/`TYPE_I`/`TYPE_ID`）。
- 建议：删除，或以它作为 `TYPE_ID` 的取值来源。

---

## 低优先级（局部可读性问题）

- L1. `TreeNode.line()`（`model/tree/TreeNode.java:58`）用 `-1` 哨兵表示缺失，名字不含「无值」语义；建议 `OptionalLong` 或改名 `lineNumber()` 并注明 `-1` 含义。
- L2. `TreeNode.parent()`（`TreeNode.java:64`）返回字符串，可能是 `RootTag` 字符串或 uuid，名字不提示二义性；建议 `parentRef()` 加 Javadoc。
- L3. `Block.obfuscated`/`deobfuscated`（`store/Block.java:18-19`）实为单字节 XOR 掩码，非真正混淆；建议 `xorMasked`/`xorPlain` 或 `masked`/`unmasked`。
- L4. `GcmSiv.decrypt(... byte[] input)`（`crypto/impl/GcmSiv.java:61`）`input` 实为 `ciphertext‖tag`；建议 `ciphertextWithTag`。
- L5. `Involution.BLOCK_LEN=8`（`crypto/impl/Involution.java:14`）与存储层 `Block` 的「block」概念冲突；建议 `FEISTEL_BLOCK_BYTES` / `KEY_ID_BYTES`。
- L6. `KeyIdIndex.index`（`crypto/impl/KeyIdIndex.java:23`）字段名与类名冗余；建议 `dekIndex`/`entries`。
- L7. `WarehouseClock.suggestedTimestamp()` vs `nextTimestamp()`（`model/vault/WarehouseClock.java:32,37`）未体现「是否按已有最大值 clamp」的差异；建议 `unclampedTimestamp()` / `timestampCappedAt(long maxExisting)`。
- L8. `SanctumHttpServer` 加解密两端把 `data` 在明文/密文间切换含义（`server/SanctumHttpServer.java:95,121,130`）；建议 `plaintextB64`/`ciphertextB64` 区分。
- L9. `SyncService.run/runBytes/runIn`（`app/sync/SyncService.java:165,169,173`）三方法动词缩写不清；建议 `execToString`/`execToBytes`/`execInDir`（或 `git`/`gitIn`）。
- L10. `RepoImporter.Result.repoRoot` vs `vaultRoot`（`app/bootstrap/RepoImporter.java:24-25`）通常相等，差异不直观；建议加字段级 Javadoc，或 `repoRoot`→`cloneRoot`。

---

## 跨模块一致性小结

| 维度 | 不一致点 | 建议统一 |
|------|----------|----------|
| 缩写大小写 | `Kdf`(唯一骆驼式) vs `KDF`/`HMAC`/`DEK` | `Argon2KDF` |
| 算法名 | `CHACHA20` 常量 vs `ChaCha20` 多处 | 常量统一 `CHACHA20`，引用常量 |
| SVG | `Svg`(类) / `SVG` / `svg` 混用 | 注释统一 `SVG` |
| `XxxTime` | `creationTime` vs `lastModTime` | `lastModificationTime` |
| icon 语义 | `icon()`(引用) vs `iconData()`(字节) | `iconRef()` / `iconData()` |
| 根对象 | `rootGroupUuid`(实为根对象) | `rootObjectUuid` |
| 公共返回类型 | `KeyInfo` 裸字段类 vs `RemoteConfig` record | `record ExternalKeyInfo(...)` |

> 注：以上为命名层面的审查结论，未改动任何源码。如需落地，建议按高优先级优先处理导出 API（H1–H5），
> 并配套更新调用点与测试。

---

## 落地记录（2026-08-26）

按风险与价值评估，已落地以下项（源码 + 调用点 + 测试均已更新，编译与测试通过）：

- **H2** `Argon2Kdf` → `Argon2KDF`（类文件、导出引用、测试类 `Argon2KDFTest` 一并改名）。
- **H3** `ExternalKeyService.KeyInfo` → `record ExternalKeyInfo(...)`（公开返回类型，调用点改用访问器）。
- **H4** `VaultForm` → `VaultDetector`（类文件与测试 `VaultDetectorTest` 一并改名；嵌套 `Type` 枚举保留）。
- **H5** 删除死方法 `ImportException.isAuthFailure()`（无任何调用方）。
- **M1** `KdbxDocument.Entry.lastModTime` → `lastModificationTime`（与 `creationTime` 对齐）。
- **M6** `SanctumGui` 私有字段 `openVaultPath`→`unlockedVaultPath`、`pendingRoot`→`targetVaultRoot`、`pendingIsNew`→`unlockIsCreate`。
  注：`current`(AtomicReference) 与 `sanctum`(普通字段) 角色不同、非冗余，且 `sanctum` 引用近 40 处，改名收益低、改动面大，故保留。
- **M10** `KdbxMapper` 字段 `ctx` → `importContext`（静态方法参数 `ctx` 不变）。
- **M11** 删除死常量 `Argon2.ARGON2id`（与 `TYPE_ID=2` 重复且未使用）。

### 本次未落地（说明原因）

- **H1** `rootGroupUuid`：JSON key `rootGroupUuid` 是磁盘 vault 的持久化格式，改名需配套迁移旧 vault，风险高，超出命名清理范围，暂缓。
- **M2** `EntryNode/GroupNode.icon()` 与 `IconNode.iconData()`：方法改名机械可行，但二者语义差异（引用 vs 字节）是当前设计意图，且改名容易误触持久化 JSON key `icon`/`data`，价值有限，暂缓。
- 其余中/低优先级（M3–M9、L1–L10）为局部可读性或常量风格问题，本次未批量处理，留待后续按需清理。
