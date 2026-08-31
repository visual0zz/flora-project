# 审查报告：sanctum 文档与注释清理（review20260831-01-sanctum）

- 日期：2026-08-31
- 模块：flora-sanctum（app / core）
- 依据：用户四条要求 + 项目 AGENTS.md 注释风格约定
  1. 文档与代码不符的部分须修正
  2. 重复/冗余的文档须合并
  3. 注释只描述代码行为/约定，不含历史演进与实现差异对比
  4. 注释按 Javadoc 格式规整

## 一、文档审查（Markdown）

### 1.1 与代码不一致（已修正）

**`addition/design/idea20260812-sanctum-treecontext-index.md`**
- 背景段原为"当前仅以 … 维护对象图；`childrenOf`（TreeContext.java:167）是全图线性扫描"。
  设计早已落地，原 :167 行号现已不对应 `childrenOf`（现为 O(1) 索引查表），且"当前"描述已不符合现状。
  改为"改造前 …"的过去式问题陈述，并删除过时行号引用。
- 设计 §1 原未说明 `indexObject` 的幂等性（该约束散落在决策文档）。补充幂等约束说明，并交叉引用
  `decision/decision20260830-02-sanctum.md`。

### 1.2 冗余合并（已合并）

- 设计文档 §1 与 `decision20260830-02-sanctum.md` 在 `indexObject`/双索引机制上存在重复描述。
  - 设计文档补全 `indexObject` 幂等约束（单一事实来源）。
  - `decision20260830-02` 删除与现状重复的内部索引机制描述，改为交叉引用设计文档，保留其独有的
    "现象→根因（KdbxMapper setIcon/rename 同 uuid 二次写入）→决策 C"叙述与验证项。

### 1.3 一致性核对（准确，未改）

- `decision20260830-01-sanctum.md`（standalone 形态判定 lib/ + edit 脚本）：`VaultDetector.detectStandaloneRoot`、
  `UserConfig(Path)`、`RepoCreator` 均经代码核对，与文档一致。
- `decision20260830-03-sanctum.md`（NodeMover DEK 重路由）：`NodeMover.move` 分派、移动组/条目的重加密范围、
  环检测沿 `parentUuidOf` 父链，均经代码核对，与文档一致。

## 二、注释清理（规则 3 + 规则 4）

仅改动注释文本，未触碰任何代码逻辑/签名/字符串/常量。按包汇总：

### core crypto（2 文件）
- `crypto/impl/SecureRandomSource.java`：删除"相比原先的单 long 累加器"对比；修正一处引用了
  不存在字段的 Javadoc（`{@code stateLock}` → `{@code synchronized}` 锁）。
- `crypto/impl/Argon2.java`：删除"兼容旧调用"演变痕迹。

### core model（8 文件）
- `EntryFields.java` / `tree/EntryNode.java`：删除"自 2026-08 起改为…""不再是 entry 负载 JSON 中的字段"
  等，改为当前约定（预设字段以独立块存储）。
- `vault/WarehouseClock.java`：删除"不再持久化到 manifest"，改为当前行为描述。
- `vault/VaultUnlocker.java`：删除"（不再静默）"。
- `LibraryConfig.java`：删除"不再落全局配置文件"，改为当前约定。
- `impl/MasterKeyRotator.java` / `impl/TreeContext.java` / `ref/RefScan.java`：删除"不再遍历全库试解"
  "取代…线性扫描""取代此前散落逻辑"等对比措辞，改为行为描述。

### core io / icon / config（4 文件）
- `io/importer/kdbx/Salsa20.java`：删除"RFC 8439 变体的替代内核"对比。
- `io/importer/kdbx/KdbxParser.java`：删除"KDF 后不再 sha256"历史说明。
- `icon/BuiltinIcons.java`：删除"使 core 不再反向依赖 app.ui"对比。
- `config/UserConfig.java`：合并两段重复且格式错误的 Javadoc 为单段标准 Javadoc（规则 4 修复）。

### core store + test（4 文件）
- `store/impl/MarkdownObjectStore.java`：删除"不再支持一个文件多个块/旧格式兼容已去除"。
- `store/Block.java`：删除"历史字段名"溯源注记。
- `crypto/GcmSivTest.java`：删除"KAT 值由替换前的 BC GCMSIVBlockCipher 生成"替代实现对比。
- `model/VaultCreatorTest.java`：删除"KEK 不再入索引"对比措辞（保留当前约定描述）。

### app ui（8 文件）
- `EntryListItem.java` / `NewVaultDialog.java` / `ModelChangeBus.java` / `SettingsModel.java` /
  `SettingsEntryRenderer.java` / `SettingsTreeRenderer.java` / `UiTheme.java` / `SanctumGui.java`：
  删除"替代过去的并行数组""替代每次 doSync 内 new SyncService""原本/不再/原 SelectScreen 入口合并进历史页"
  等历史/重构对比表述，仅保留当前行为/约定描述。

### app server / bootstrap / sync（0 改动）
- 经逐文件核对，`SanctumHttpServer` / `RepoCreator` / `RepoImporter` / `VaultDetector` / `SyncService` /
  `Main.java` / `module-info.java` 注释均描述当前行为与约定，无规则 3/4 违规。

## 三、编译验证

`mvn.cmd -pl flora-sanctum -am test-compile -DskipTests` → **BUILD SUCCESS（EXIT=0）**。
整个模块（含测试源码）注释改写未破坏任何 Java 语法。

## 四、保留说明（非本次审查范围）

- `Main.java` 与 `io/importer/ImportListeners.java` 中新增的日志代码（`Logger`/`LogSetup`/`LOG.info|warn`）
  由用户另行启动的 agent 完成，不属于本次文档/注释审查。已与用户确认保留，且编译通过、功能正常。
- 审查与清理过程中，对上述两文件曾误作"越界改动"还原，已按用户指示恢复其日志改动。

## 五、统计

- 改动文件：30 个（28 个 .java + 2 个 .md）
- 其中：文档修正 2 个、注释清理 26 个（规则 3 历史/对比删除为主，规则 4 格式修正 1 处）、用户既有日志代码 2 个（保留）
