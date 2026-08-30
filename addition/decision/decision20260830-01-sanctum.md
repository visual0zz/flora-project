# 决策：sanctum 孤立形态判定从 standalone.json 改为 lib/ + edit 脚本

- 日期：2026-08-30
- 模块：flora-sanctum（app / core）

## 背景

孤立（standalone）模式原先以仓库根存在的 `standalone.json` 作为形态判定标记，同时它又兼任仓库级配置文件（主题、自动锁定时长等"使用习惯"内容，非机密）。该文件由 `RepoCreator` 在创建/升级独立仓库时写入，`jar` 启动时检测自身同目录/工作目录是否存在它来分流。

用户要求：将孤立模式的启动脚本从单一 `start.cmd` 分裂为 `edit`（posix）与 `edit.bat`（windows）两个文件，并删除 `standalone.json`，改为由 `jar` 判定「自身是否位于某 `lib/` 目录」且「仓库根是否存在 `edit` 脚本」来决定是否以孤立形态启动。

## 决策

1. **删除 `standalone.json` 作为形态标记**：孤立形态不再依赖任意标记文件，改为结构判定。
2. **孤立形态判定**（`VaultDetector.detectStandaloneRoot`）：`jar` 所在目录名为 `lib` 且其父目录（仓库根）存在 `edit` 或 `edit.bat` 脚本 → 孤立形态；否则应用形态。判定逻辑全部落在 `jar` 内，不依赖脚本传参。
3. **启动脚本分裂**：`RepoCreator` 不再写出 `start.cmd`，改为同时写出 `edit`（bash）与 `edit.bat`（cmd）两个独立文件。`detect`/`isStandaloneRepo` 以 `lib/` + `edit` 脚本作为独立仓库布局判据。
4. **仓库级配置落点**：因 `standalone.json` 被删除，其原本兼任的仓库级配置职责转移到仓库根 `config.json`（与应用级 `~/.flora-sanctum/config.json` 同结构）。`UserConfig(Path)` 直接读仓库根 `config.json`，不再有 `standalone.json` 分支。

## 理由

- 用户明确要求删除 `standalone.json` 并以 `lib/` + `edit` 脚本判定，减少一个"魔法标记文件"，判定更贴合实际部署结构（独立仓库必然自带 `lib/` 与 `edit` 脚本）。
- 仓库级配置改用 `config.json` 是最小且一致的改动：`UserConfig` 原本就有一层 `standalone.json` 优先、`config.json` 回退的逻辑，去掉前者即可，不影响应用级配置路径。

## 影响

- 分发 zip（`distribution.xml`）保留 `start.cmd` 作为应用形态启动器（非 `edit` 脚本，故不会被判定为孤立形态）；独立仓库的 `edit`/`edit.bat` 由应用内"配置独立运行"生成。
- 既有独立仓库（旧 `standalone.json` + `start.cmd`）升级前不被识别为孤立形态，会被当作普通仓库打开，其旧 `standalone.json` 中的仓库级配置不再被读取——属本次重构的预期破坏性变更。
- 测试 `VaultDetectorTest` 各用例改用 `config.json` 与 `edit`/`edit.bat` 表达独立布局。
