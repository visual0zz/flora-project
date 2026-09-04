# 代码审查：flora-sanctum 中 app 与 core 的责任错位

- 日期：2026-09-04
- 范围：`flora-sanctum-core` 与 `flora-sanctum-app` 全部 `src/main` 代码
- 方法：两个并行探查 agent 全量扫描 + 人工抽查关键行号复核（已修正 agent 误判）

## 边界定义

- **core（库，可复用，无 UI 依赖）**：加密/存储/数据模型与树/导入导出格式。
  已记录的硬约束：core 不得自己 `LoggerFactory.getLogger`、不得感知日志落盘/XDG 路径，
  Logger 必须由外部传入。
- **app（宿主）**：Swing GUI、启动引导 `bootstrap`、同步 `sync`、HTTP 服务 `server`。

方向 A = core 做了本属宿主的事（core 越界）；方向 B = app 做了本属领域的逻辑（app 越界）。

---

## A. core 越界到宿主职责

### A1. KdbxImporter 自己取 Logger（违反已记录约束，最高优先级）
`core/io/importer/kdbx/KdbxImporter.java:55` 直接 `LoggerFactory.getLogger(KdbxImporter.class)`。
而 `core/io/importer/ImportContext.java` 全文没有 logger 字段——core 内部其它导入路径
（如 SanctumGui 注入的 listener）走的是 app 提供的 logger，唯独 KDBX 这一路由 core 自建。
这与项目已定的「core 不自己取 Logger」原则冲突，且破坏了日志链路一致性。
**修复**：给 `ImportContext.Builder` 加 `logger(...)`，`KdbxImporter` 改用 `ctx.logger()`。
迁移成本：低。

### A2. UserConfig 放错了层（上轮已确认）
`core/config/UserConfig.java`：硬编码 `user.home/.flora-sanctum`，内容全是 GUI 偏好
（theme/windowSize/dividerRatio/recentVaults），core 内部零引用，仅 app 使用。
**修复**：迁到 `app/config`。（已与用户确认方向，待执行。）

### A3. 展示型枚举 + 硬编码中文名进了数据模型
- `core/model/ViewNodeType.java`：注释自陈「纯 UI 区段 / 虚拟根标记，不持久化」，却带
  `displayName()` 中文（`密码库/图标/SSH 密钥/...`）。**修正 agent 误判**：该枚举并非
  「core 零引用」——`core/model/StoredNodeType.java:14-32` 的 `view()` 字段持有它，即数据层
  枚举反向引用了 UI 区段枚举。要让 core 不感知 UI，需 `StoredNodeType` 去掉这个引用
  （改为返回轻量字符串或交由 app 做映射）。
- `core/model/TrashView.java:72-87` `TrashKind` 中文 label、`93-124` `originalPath()` 拼
  `密码库/` 前缀与 `未知(uuid前8位)`/`未命名`。节点「是否进垃圾桶」是领域概念（保留），
  但展示文案与路径拼装是渲染职责，且无 Locale 出口。
**修复**：枚举 displayName/label 与 originalPath 的展示层处理移到 app；core 只保留分类判定。
迁移成本：低-中。

### A4. 全局可变静态缓存 + 感知自身打包形态
`core/icon/BuiltinIcons.java:25,34,46-52,72-84`：持有 `volatile static` 图标名缓存，
并按 module layer / classpath / jar 三种形态分支扫描。库不应推断自己被如何打包，也不该
持有不可清理的全局缓存。注：KDBX 导入映射会用到它（`names()` 供图标名匹配），故放 core 有
部分合理性——重点改的是「打包形态分支」与「静态全局缓存」，而非整体迁出。
**修复**：缓存改为实例/可注入，扫描分支收敛。迁移成本：低。

### A5. 模块声明里写了宿主路径
`core/src/main/java/module-info.java:20` 注释「用户配置目录（~/.flora-sanctum）」随 A2 一并删除。

### A6.（提示）core 注释反向引用 app 类
`core/store/VaultProbe.java:18` javadoc 提到 `app.bootstrap.VaultDetector`。代码无依赖，
但属于库文档牵连宿主层，建议删此反向引用注释（详见 B1）。

---

## B. app 越界到 core 职责（领域逻辑应下沉）

### B1. 仓库判据在两处分裂（最该修）
`app/bootstrap/VaultDetector.java:93-99` `hasBlockFiles` 用 `*.md` 后缀自己判定「是否含数据块」，
而 `.md` 块文件与「两层分片目录」是 core `store` 层的私有格式。core 已有同类判据
`VaultProbe.java:26-52`，但只认 hex 分片目录 + `lib/`，不认 md——同一「是否仓库」判据在两处
独立漂移（且 VaultProbe 注释把形态识别整体推给 app，固化此缺口）。
同类：`app/sync/SyncService.java:56-80` `isFullyManaged` 也硬编码 `.md`。
**修复**：core `store` 补「含数据块」判据，`VaultDetector`/`SyncService` 只做组合。迁移成本：低。

### B2. 存储编码格式泄漏到 UI
`app/ui/SanctumGui.java:1415-1427` `parseUuid`（无连字符 hex ↔ 规范 UUID，parent 字段存储格式）、
及同文件 `folderPathOf`/`fieldStoragePath`/`isTopLevel`/`groupIdOf`/`groupsById` 区段
（`folderPathOf` 区约 1866-1888、`fieldStoragePath` 约 2391-2409 等）：全部靠
`entry.parentRef()` 裸字符串自己拼。core 已有 `model/util.UuidHex` 与 `model/ref.NodeRefResolver`，
UI 不应知道 parent 的 hex 编码。
**修复**：core `ObjectTree` 提供 `pathOf(uuid)/parentOf(uuid)/isTopLevel(uuid)`。
迁移成本：中（纯函数、无 EDT 依赖，可整块搬）。

### B3. 领域查询写在 UI 里
- `SanctumGui.java:2510-2522` `totpFields` 全树扫描收集 `kind:"totp"` 字段，每次刷新重跑；
  对称地外部密钥聚合有 core `ExternalKeyService.list()`，TOTP 没有。**修复**：core 加 `TotpView`
  （与 `TrashView` 同级，返回 `List<FieldNode>`）。迁移成本：低。
- `SanctumGui.java:3196-3213` `detachKeyRefs` 删除 SSH 密钥前遍历 `remoteTree` 清 `keyRef`——
  引用完整性约束应是数据模型职责（core 已有 `model/ref/RefScan` 与 `MasterKeyRotator`）。
  **修复**：core 返回 `List<RemoteNode>`，UI 只弹确认框。迁移成本：中（方法体内混了 `JOptionPane`，先切分）。

### B4. 仓库配置序列化在 UI 手工完成
`SanctumGui.java:3953-3963` `configForStandalone` 把 `LibraryConfig` 三字段逐个 `put` 成 JsonObject，
core 给 `LibraryConfig` 加字段时 app 会静默漏掉。**修复**：core 的 `LibraryConfig` 提供
`toJson()`/`fromJson()`。迁移成本：低。

### B5. 参数校验只在 UI 做
`app/ui/KdfParamsPanel.java:80-104` 与 `app/ui/SettingsModel.java:199-206,237-244`：KDF 参数只校验
「非空且 >0」，超时只 `parseInt` + catch；而 `LibraryConfig` setter 现在连负数都收。取值约束
应属于 `core/crypto/Argon2KDF` 与 `LibraryConfig` 自身。**修复**：core 提供 `validate(...)` 与
setter 内校验。迁移成本：低。

---

## C. core 内部时间源分散（设计缺陷，顺带记录）

core 已有权威时钟 `TreeContext.nextTimestamp()` → `Vault.clock().timestampCappedAt(...)`（单调不回退），
但以下 8 处直接 `System.currentTimeMillis()`，会绕过锚点、可能写入早于仓库锚点的时间戳：
`model/tree/ObjectTree.java:114`、`FieldNode.java:74`、`EntryNode.java:119,130,167,207,223`、
`model/vault/VaultUnlocker.java:200`。这既是 core 内部不一致，也属于「时间源职责」未收敛。
**修复**：统一改为经由 `TreeContext`/`WarehouseClock`。迁移成本：中。

---

## 已排查判定合理的（避免误报）

- `ImportListeners.java` 的 `System.out.println`：**非默认行为**；`ImportContext` 默认 `noop()`，
  `console()` 为显式 opt-in，仅测试使用。建议将其下沉为 test-fixture，但非责任错位。
- `ExternalKeyService`：注释提 HTTP，但代码只做加解密/Base64/keyId 路由，无网络调用，属数据层。
- `server/SanctumHttpServer.java:117,144` 的 Base64：仅 HTTP JSON 传输编码，加解密全部走 core
  `ExternalKeyService`，未碰密钥/块头/keyId。合理。
- `bootstrap/RepoCreator`（复制 jar/lib、生成脚本）、`RepoImporter`（`git clone` 进程调用）：
  宿主分发与进程编排，与 core 无关。合理。
- `ui/PasswordStrength`（zxcvbn 启发式）、`ModelChangeBus`（UI 脏标记总线）、`NewVaultDialog`
  （只收集输入，已复用 `VaultProbe.markers`）：纯 UI/宿主，合理。
- `SanctumGui` 的解锁/锁定/导入导出/移动/剪贴板：均直接委托 core 公开 API，属正常调用。合理。
- `VaultDetector` 的 standalone 形态判定（lib/+edit 脚本）：宿主部署形态，已明确划归 app。仅
  `hasBlockFiles` 一处越界（见 B1）。
- `StoreNodeType.view()` 返回 `ViewNodeType`：见 A3，属反向引用而非「合理」，单列说明。

---

## 建议处理顺序

1. A1（KdbxImporter Logger）——违反硬约束，改动最小，先做
2. A2 + A5（UserConfig 迁移 + module-info 清理）——上轮已确认
3. B1（仓库判据收敛）——消除判据漂移，低风险
4. B2/B3/B4（领域查询与序列化下沉）——降低 UI 体积、统一数据出口
5. A3/A4（展示枚举与打包感知）——界面概念清理
6. C（时间戳统一）——修正潜在数据缺陷
7. B5（校验下沉）——收尾
