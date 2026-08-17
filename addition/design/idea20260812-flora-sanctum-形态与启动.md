# flora-sanctum 应用形态与启动流程设计

日期：2026-08-12
状态：方案稿（待评审）

## 1. 背景与目标

当前启动流程：`Main` 无参 → `SanctumGui.launch` → 解锁屏（最近库列表）→ 三栏主界面。首次使用缺乏"新建/导入/打开"引导；分发形态是"并列多 jar + 启动脚本 + zip"（需预装 Java），但缺少标准化的分发 zip 与"独立仓库"形态。

目标：

- **首次进入**进入选择界面（新建 / 导入 / 打开），而非直接解锁屏；
- 引入**仓库形态**：普通仓库（纯 `data/*.md`）与**独立仓库**（自包含 `lib/` 全量 jar + `data/` + 多平台启动脚本 + 仓库级配置，免装应用形态、依赖本地 `java`）；
- **仓库级配置**：与应用级配置同结构，独立仓库不读系统配置、只读自身配置；
- **导入**：`git clone` 远程到本地，按结构分类（非仓库 / 普通仓库 / 独立仓库），空仓库建立基本结构；
- **分发 zip**：配置进 Maven pom（`lib/` 全量 jar + 多平台启动脚本 + 应用配置）。

## 2. 两种应用形态（同一 jar，靠仓库级配置区分）

| 形态 | 结构 | 启动行为 |
|---|---|---|
| **应用形态**（分发 zip） | `zip: { lib/全量jar, start.cmd, start.sh, config.json }` | 首次进入 → 选择界面（新建/导入/打开） |
| **独立仓库形态**（自复制） | `文件夹: { lib/全量jar, data/, start.cmd, start.sh, config.json }` | 直接 → 解锁界面（输密码或退出），无选择界面 |

- **独立仓库不带 JRE**：靠本地 `java` 指令 + `lib/` 全量 jar 运行；脚本按 module-path 拼 `requires`（保持 JPMS 模块化）。
- **打开独立仓库 = 当前 jar 直接加载它的 `data/`**（复用当前 lib，不转跑它自己的脚本）——"打开"一律是当前进程加载对应 `data/`；独立仓库自己的脚本只用于"脱离应用形态、单独启动该仓库"。
- **新建独立仓库** = 把应用自身复制成 `{ lib/, data/, 脚本, 配置 }`，之后可由该仓库自己的脚本启动。

## 3. 仓库级配置与应用级配置

两者**同结构**（`UserConfig` 的 JSON：theme/lockTimeoutSeconds/clipboardClearSeconds/syncEnabled/recentVaults/lastVault）。

- **应用级**：`~/.flora-sanctum/config.json`（用户级，跨所有库一份）。
- **仓库级**：`<repo>/config.json`（该仓库一份）。
- 新建独立仓库时**复制应用级配置过去**，但**不含密钥地址等加密信息**（那些在 vault 内 SECRET 对象，见 05）。
- 独立仓库形态**只读自身仓库级配置，不读系统配置**。

## 4. 启动分流（Main）

```
Main.main(args)
├── 有参数 → flora-shell CLI（现状不变）
└── 无参数 → 启动形态识别
    ├── 位于独立仓库内（当前目录存在仓库级 config.json + data/）
    │     → 直接加载该仓库 data/，进入解锁界面（不出现选择界面）
    └── 应用形态
          → 进入选择界面：新建 / 导入 / 打开
```

形态识别依据：**仓库级配置文件是否存在 + 目录结构**。

## 5. 选择界面（应用形态首次进入）

三个入口：

| 入口 | 行为 |
|---|---|
| **新建** | 弹窗选择建立哪种仓库：普通仓库 / 独立仓库 |
| **导入** | 输入远程仓库地址 + 本地文件夹位置 → `git clone` → 按结构分类处理 |
| **打开** | 选择已存在的仓库目录（普通/独立），加载其 `data/` 进入解锁界面 |

### 5.1 新建仓库

- **普通仓库**：在当前选中目录建 `data/` 结构（纯 markdown 块），用当前 jar 加载。
- **独立仓库**：把应用自身复制为 `{ lib/全量jar, data/, start.cmd, start.sh, config.json }`，复制应用级配置为仓库级配置（不含密钥），**不打开**（由用户之后用该仓库脚本启动）。

### 5.2 导入仓库

`git clone <remote> <local>` → 克隆后按结构判断：

| 判定 | 结构 | 处理 |
|---|---|---|
| **非仓库** | 无 `data/`、无 `lib/`、无仓库配置 | 判定为"输入了别的代码仓库地址" → 报错提示（非本应用仓库） |
| **空仓库** | 克隆后为空目录 | 视为合法，建立基本结构（按普通仓库或独立仓库，依据选择/默认） |
| **普通仓库** | 有 `data/*.md`，无 `lib/`/脚本 | 直接加载其 `data/` 进入解锁界面 |
| **独立仓库** | 有 `data/` + `lib/` + 脚本 + 仓库配置 | 直接加载其 `data/` 进入解锁界面 |

## 6. 打包：分发 zip 进 Maven pom

`flora-sanctum-app/pom.xml` 增加 `maven-assembly-plugin`（或复用现有 `maven-dependency-plugin` 的 `target/lib`）：

```
flora-sanctum-0.1.zip
├── lib/            # 全量依赖 jar（app/core/shell/root + bc/flatlaf/jsvg）
├── start.cmd       # Windows 启动：java --module-path lib -m com.flora.sanctum.app/com.flora.sanctum.app.Main
├── start.sh        # Linux/macOS 启动：同上
└── config.json     # 应用级默认配置（新建独立仓库时复制为仓库级）
```

- `maven-dependency-plugin` 已把依赖复制到 `target/lib`；assembly 把 `target/lib` + 脚本模板 + 配置打进 zip。
- 启动脚本用 **module-path**（非 classpath），保持 JPMS 模块化。

## 7. 需新增/改动的代码

| 文件 | 改动 |
|---|---|
| `Main.java` | 无参时先做形态识别，再决定"选择界面"或"直接解锁" |
| 新增 `app/bootstrap/VaultForm.java` | 仓库形态识别（普通/独立/非）、仓库级配置读写 |
| 新增 `app/ui/SelectScreen.java` | 选择界面（新建/导入/打开） |
| `SanctumGui.java` | 支持"指定仓库路径直接进解锁"的入口；解锁屏只显示该仓库，不显示系统最近库列表 |
| `UserConfig` | 仓库级实例化（读 `<repo>/config.json`），与应用级同结构 |
| 新增 `app/bootstrap/RepoCreator.java` | 新建普通/独立仓库（独立=自复制） |
| 新增 `app/bootstrap/RepoImporter.java` | git clone + 结构分类 + 空仓库建结构 |
| `pom.xml` | 加 maven-assembly-plugin 打分发 zip；加启动脚本模板资源 |
| 资源 `start.cmd` / `start.sh` 模板 | 多平台启动脚本 |

## 8. 决策点与开放问题

1. **独立仓库脚本 module-path 组装**：`lib/` 里 jar 较多，脚本需逐个列出 `requires` 或按目录通配——`java --module-path lib -m ...` 是否可接受（JDK 会扫描 `lib/` 下所有 jar 作为模块路径）？待验证。
2. **"空仓库建立基本结构"的默认类型**：导入空仓库默认建普通仓库还是独立仓库——建议普通仓库（简单），用户可事后升级。
3. **选择界面形态**：Swing 单窗口（三个按钮）即可，还是需要向导式？建议单窗口。
4. **独立仓库的 `data/` 定位**：独立仓库内 `data/` 即 `Sanctum.root`；普通仓库的 `Sanctum.root` 是用户选定的含 `data/` 的目录，需统一"仓库根 = 含 data/ 的目录"。

## 9. 关联文档

- 用户配置目录：设计 07
- 仓库形态与 Git 关系：04b
- 库形态判定：06
- 分发打包：07 UI 设计"可分发包"
