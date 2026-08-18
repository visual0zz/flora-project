# flora-sanctum UI 交互现状梳理

日期：2026-08-18
类型：现状梳理（交互流程基线，供调整交互时对照）

> 本文描述当前代码的实际交互逻辑，代码位置均已标注，调整交互时以本文为基线。

## 1. 启动分流（Main.main）

`flora-sanctum-app/src/main/java/com/flora/sanctum/app/Main.java`

GUI 是唯一交互入口，启动即做形态识别：

```
Main.main
└── 读系统属性 flora.repo（启动脚本 -Dflora.repo 传入；未传 → 当前工作目录）
    ├── VaultForm.detect(repoRoot)
    │   ├── STANDALONE（目录含 data/）→ SanctumGui.launchDirect：直接解锁该仓库
    │   └── 其它 → SelectScreen：选择界面（新建/导入/打开）
```

关键点：
- 启动脚本区分两种形态：**分发版**脚本不带 `-Dflora.repo`（进选择界面）；**独立仓库**自带脚本带 `-Dflora.repo`（直接解锁）。
- 原 CLI 命令入口（flora-shell）与 `command` 包已移除，`SanctumGui` 不接受命令行参数。

## 2. GUI 生命周期（SanctumGui.run）

`app/ui/SanctumGui.java:127`

1. 应用主题（`config.theme()`：light/dark/system）
2. 启动本地 HTTP 服务（127.0.0.1 随机端口，随 GUI 退出而停止）
3. 创建主窗口 + 系统托盘
4. 内容面板：**解锁屏** → 解锁成功 → **主界面**；锁定 → 回解锁屏

窗口尺寸切换：解锁屏 `520x400` → 主界面 `960x640`。

**系统托盘**（`SanctumGui.java:212`）：锁定 / 复制密码（复制最近一次复制的明文）/ 退出。托盘随 GUI 启动安装。

## 3. 解锁屏（buildUnlockPanel）

`app/ui/SanctumGui.java:242`

布局：标题 → 中部（最近库列表 + "打开其他库…"）→ 底部（主密码框 + "解锁 / 新建"按钮 + 错误/提示行）。

### 应用形态（无 directVaultRoot）

| 交互 | 行为 |
|---|---|
| 选中最近库 | 显示"库：<目录名>"，清空密码框并聚焦（`SanctumGui.java:315`） |
| 打开其他库… | 目录选择器 → `config.addRecentVault` + 刷新列表并选中（`SanctumGui.java:329`） |
| 输入密码回车 / 点按钮 | `doUnlock`（`SanctumGui.java:340-355`） |
| 预选 | 启动时选中 `config.lastVault()`（若在列表中，`SanctumGui.java:358`） |

最近库列表数据：`config.recentVaults()`（上限 10，最近在前，见 `UserConfig.java:100`）。

### 独立仓库形态（directVaultRoot 非空）

隐藏最近库列表与"打开其他库"；标题显示指定库名；按钮文案变"解锁"；自动聚焦密码框（`SanctumGui.java:363-369`）。解锁路径直接用 `directVaultRoot`，不经过选择逻辑。

### doUnlock 逻辑（`SanctumGui.java:381`）

```
输入密码
├── 空密码 → 提示"请输入主密码"
├── Sanctum.open(root)
│   └── sanctum.unlock(pw)
│       └── 异常且 message 含 "no manifest" → Sanctum.createAndUnlock(root, pw)（新建库）
├── 成功 →
│   ├── config.addRecentVault + setLastVault
│   ├── 窗口标题 "flora-sanctum(<目录名>)"
│   ├── current.set(sanctum)
│   ├── 切到主界面（960x640）
│   └── 启动自动锁定计时器
└── 任何失败 → 统一提示"解锁失败"（不区分密码错/数据损坏，见设计 03）
```

## 4. 自动锁定与剪贴板清空

### 自动锁定（`SanctumGui.java:417`）

- 解锁后启动 `Timer`，时长 `config.lockTimeoutSeconds()`（默认 **300 秒**）。
- 所有主要交互（树/列表选择、保存、删除、复制、新建、同步等）都调用 `resetAutoLock()` 重置计时器——只要库仍解锁。
- `lock()`（`SanctumGui.java:436`）：`sanctum.close()`、清 current/路径、停全部计时器、回解锁屏。被托盘"锁定"、主界面"锁定"/"切换库"触发（切换库 = 先锁定）。

### 剪贴板清空（`SanctumGui.java:1310`）

复制密码后启动 `Timer`，`config.clipboardClearSeconds()`（默认 **30 秒**）后将剪贴板置空。

## 5. 主界面（buildMainPanel）

`app/ui/SanctumGui.java:455`

三栏布局（`JSplitPane`，分隔线按初始比例记忆，`keepDividerRatio`）：

```
┌ 顶部工具栏（图标按钮 + 搜索框 + 状态栏）┐
├─────────┬──────────┬───────────────────┤
│ 左：组树  │ 中：条目列表 │ 右：编辑面板        │
│ 四区段    │ 子文件夹+条目 │ 条目/文件夹/详情     │
└─────────┴──────────┴───────────────────┘
```

### 顶部工具栏（`SanctumGui.java:459-497`）

图标按钮（SVG + tooltip，无文字标签）：

| 按钮 | 触发行为 |
|---|---|
| 新建条目 | `doNewEntry` |
| 新建文件夹 | `doNewGroup` |
| 删除 | `doDelete` |
| 同步 | `doSync`（仅"完全托管"时显示） |
| 设置 | `openSettings` |
| 切换库 | `switchVault` = 锁定回解锁屏 |
| 锁定 | `lock` |
| 导入图片 | `doImportImage`（仅"图标"区段显示） |
| 添加 SSH 密钥 | `addSshKey`（仅"SSH 密钥"区段显示） |
| 添加远程 | `addRemote`（仅"远程"区段显示） |
| 搜索框 | 回车刷新列表；× 清空 |

**工具栏动态显隐**（`updateToolbar`，`SanctumGui.java:607`）：
- 按当前树选中的区段切换 导入图片 / 添加SSH / 添加远程 的可见性。
- "新建条目"仅在选中普通文件夹时可用；"新建文件夹"在密码库根或普通文件夹时可用。
- 删除恒可用（对根区段选择会提示"根组不允许删除"）。

### 左：组树（`rebuildGroupTree`，`SanctumGui.java:632`）

- "全部"根隐藏，四个顶层区段（`RootTag`）：
  - **密码库**（DATA）：顶层文件夹 + 递归子文件夹
  - **图标**（ICON）
  - **SSH 密钥**（SSH_KEY）
  - **远程**（REMOTE）
- 选中节点触发：重置自动锁定 → 更新工具栏 → 刷新条目列表 → 若选中的是文件夹则右侧显示文件夹编辑面板（`SanctumGui.java:517`）。

### 中：条目列表（`refreshEntryList`，`SanctumGui.java:696`）

内容随区段/文件夹变化：
- **密码库**：当前文件夹的子文件夹 + 条目（混合，顶层为 rootGroups + rootEntries）
- **图标区**：图标列表（显示"图标 [格式]"）
- **SSH 区**：密钥名列表
- **远程区**：远程名列表

交互：
- 单击 → 右侧编辑面板（`showSelectedEntry`：group→文件夹面板；icon/sshKey/remote→只读信息；entry→条目编辑）
- 双击子文件夹 → 进入该文件夹（树联动选中，`navigateToGroup`，`SanctumGui.java:548`）
- 渲染：文件夹用文件夹图标，其余用条目图标（`EntryListRenderer`）

### 右：编辑面板

**条目编辑**（`renderEntry`，`SanctumGui.java:947`）：
- 名称 / URL / 用户名 / 密码 / 标签 五个内置字段（标签为逗号分隔文本，`EntryFields.labelsToString`）
- 创建时间 / 更新时间（只读，斜体灰）
- 自定义字段区：每个字段一行 = 字段名 + 值输入框 + kind 下拉 + 删除按钮；kind=totp 时行内显示当前验证码
- 操作行：**保存** / **+ 添加字段** / **复制密码** / **选择图标**

**文件夹编辑**（`renderGroupPanel`，`SanctumGui.java:892`）：名称输入 + 保存（重命名）+ 删除文件夹。

**图标 / SSH / 远程详情**：只读信息标签（`renderIconPanel` / `renderSshKeyPanel` / `renderRemotePanel`）。

## 6. 核心操作流程

### 新建条目（`doNewEntry`，`SanctumGui.java:1463`）

不弹对话框：直接以空白名"新建条目"创建（含空密码字段）→ 选中并在编辑面板打开 → 提示"已新建条目，请填写名称与密码"。前置：必须已选中一个密码库文件夹，否则提示。

### 新建文件夹（`doNewGroup`，`SanctumGui.java:1493`）

同样不弹对话框：直接以"新建文件夹"创建 → 树选中并打开文件夹编辑。仅允许在密码库根或普通文件夹下；图标/SSH/远程区段禁止。

### 删除（`doDelete`，`SanctumGui.java:1514`）

- 优先删条目列表中选中的对象（按类型文案：条目/该文件夹/该图标/该 SSH 密钥/该远程配置）→ 确认对话框 → 删除。
- 无列表选中时：树选中文件夹 → 确认 → 递归删除。
- 选中区段根（图标/SSH/远程/密码库根）→ 提示"根组不允许删除"。

### 搜索（`SanctumGui.java:696` 过滤逻辑）

对当前列表内容按查询串过滤：文件夹按名称；条目按名称 / 字段名 / 字段值。只过滤当前区段/文件夹，不跨层级。

### 复制密码（`copyPassword`，`SanctumGui.java:1287`）

复制条目 password 字段到剪贴板，记录明文供托盘"复制密码"复用，启动清空计时器。未设置密码提示"未设置密码"。

### 添加字段（`addFieldDialog`，`SanctumGui.java:1157`）

对话框：字段名 / 值 / 类型（kind 下拉）。kind 选项 = 预定义 `FieldKind` + 库内出现的未知 kind（向后兼容）。字段名必填。

### 选择图标（`chooseEntryIcon`，`SanctumGui.java:1427`）

从已导入图标列表弹出选择框，选中后 `entry.setIcon(uuid)`。无图标时提示先到"图标"区导入。

### 导入图片（`doImportImage`，`SanctumGui.java:1329`）

文件选择器（png/jpg/jpeg/gif/webp/svg）→ 读字节 → `iconTree().createIcon`。SVG 存原始文本；其余格式先 `ImageIO.read` 校验可读。

### 添加 SSH 密钥（`addSshKey`，`SanctumGui.java:1362`）

对话框：名称 + 私钥（PEM 多行文本）。名称与私钥必填 → `sshKeyTree().createSshKey`。

### 添加远程（`addRemote`，`SanctumGui.java:1394`）

对话框：名称 / URL / SSH 密钥引用（可空）→ `remoteTree().addRemote`。名称与 URL 必填。

### 同步（`doSync`，`SanctumGui.java:1571`）

```
检查 isFullyManaged（库根全部为 .md，见 SyncService.isFullyManaged）
├── 否 → 状态栏"非完全托管，跳过同步"
└── 是 → sanctum.close() → SyncService.sync() → Sanctum.open() 重新打开
         （git init + commit 全部 + fetch + rebase + 冲突按块时间戳仲裁 + push）
          成功→"已同步"；失败→"同步失败"
```

### 设置（`openSettings`，`SanctumGui.java:1596`）

模态对话框三组配置（保存各自独立按钮）：

| 配置 | 默认 | 存储键 |
|---|---|---|
| 主题（light/dark/system） | system | `theme` |
| 自动锁定（秒） | 300 | `lockTimeoutSeconds` |
| 剪贴板清空（秒） | 30 | `clipboardClearSeconds` |

> 注意：`syncEnabled` 配置项存在（`UserConfig.java:77`）但设置对话框**没有对应开关**；同步按钮可见性只看 `isFullyManaged()`，不看 `syncEnabled`。

## 7. 选择界面（SelectScreen）

`app/ui/SelectScreen.java:40`

仅应用形态无参数启动时出现。三个按钮：

### 新建（`newVault`，`SelectScreen.java:71`）

1. 选择类型：普通仓库 / 独立仓库
2. 目录选择器
3. 普通仓库 → `RepoCreator.createNormal` → 立即打开
4. 独立仓库 → `RepoCreator.createStandalone`（复制 lib + 写跨平台启动脚本 + 复制应用配置为仓库级）→ 弹窗"请用仓库内的启动脚本（start.cmd）启动" → 关闭选择界面（**不打开**）

### 导入（`importVault`，`SelectScreen.java:104`）

表单：远程地址 + 本地目录 → `RepoImporter.importRemote`：

```
git clone <remote> <local>
├── 克隆后为空目录 → 建基本结构（普通仓库）→ 打开
├── detect 为普通/独立仓库 → 打开 vaultRoot
└── 其它 → 报错"not a flora-sanctum repository"
```

### 打开（`openVault`，`SelectScreen.java:135`）

目录选择器 → `VaultForm.detect` 为非仓库 → 报错"该目录不是 flora-sanctum 仓库"；否则打开 `vaultRoot`。

## 8. 配置数据（UserConfig）

`flora-sanctum-core/src/main/java/com/flora/sanctum/config/UserConfig.java`

- **应用形态**：`~/.flora-sanctum/config.json`；**独立仓库形态**：`<仓库根>/config.json`（`SanctumGui.java:100-107`）。
- 完整键：`theme`、`lockTimeoutSeconds`、`clipboardClearSeconds`、`syncEnabled`、`recentVaults`（上限 10）、`lastVault`。
- 不存放任何密钥/密文。

## 9. 本地 HTTP 服务（SanctumHttpServer）

`app/ui/SanctumGui.java:130` 启动；`app/server/SanctumHttpServer.java`

- 绑定 `127.0.0.1` 随机端口，POST JSON。
- 端点：`/keys/list`、`/crypt/encrypt`、`/crypt/decrypt`（外部密钥加解密，见 `ExternalKeyService`）。
- 锁定时（sanctum 为 null 或未解锁）所有端点返回 `locked`。
- 只读密钥能力，无编辑/仓库管理端点。随 GUI 生命周期启停。

## 10. 主题（UiTheme）

`app/ui/UiTheme.java` + `SanctumGui.java:167`

- 主题三态：dark → `FlatDarkLaf`；light/system-dark → `FlatLightLaf` + 纸感 `UiTheme`。
- 纸感风格：暖白纸底（`#F8F4E9`）、暖浅控件底、纯白输入框、淡灰棕文字/分隔线；`PaperPanel` 用 `PaperNoise` 噪声底纹。
- 选择界面、解锁屏、主界面均用 `PaperPanel` 根面板。

## 11. 交互现状疑点（调整时参考）

以下为梳理时发现的不一致/可能调整点，均为**现状**，未改动：

1. **设置对话框没有 `syncEnabled` 开关**，但配置项存在（`UserConfig.java:77`）。
2. **同步按钮**可见性只看 `isFullyManaged()`，不看 `syncEnabled` 配置。
3. **解锁屏"解锁 / 新建"按钮**在独立仓库形态下文案变"解锁"，但应用形态下"新建"与解锁同一按钮（路径不存在时自动新建），语义可能需明确。
4. **条目列表的远程区**元素 `listItemTypes` 标为 `"field"`（`SanctumGui.java:725`），与实际类型 remote 不一致，双击导航逻辑不适用于远程区（远程条目不可双击进入）。
5. **图标详情面板**仅显示格式与提示，无预览图；SSH/远程详情面板均无编辑入口。
6. **自动锁定计时器**在设置对话框打开期间不重置（对话框无交互钩子）。
7. **搜索**仅过滤当前区段/文件夹，不能全局搜索。
8. **复制密码只复制 password 内置字段**，自定义字段无"复制"按钮（仅 TOTP 显示验证码文本）。
