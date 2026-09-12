# 决策：独立仓库不再持有仓库级 config.json

日期：2026-09-13
模块：flora-sanctum

## 决策

独立仓库（lib/ + edit 脚本形态）**不再写入/读取仓库根 `config.json`**；明文偏好统一走系统级配置
（`UserConfig`，`~/.config/flora-sanctum/config.json`，遵循 `XDG_CONFIG_HOME`）。
仓库内**加密配置**（`type=config` 节点 / `LibraryConfig`：自动锁定、剪贴板清空等）不受影响。

## 背景与原因

- 先前「应用形态」与「独立仓库形态」各持一份同结构的明文 config.json，等于同一组偏好存在两份，
  需要额外的复制/同步，且容易分叉（此前刚做过一轮"明文层与密文层不重叠"的去重）。
- 独立仓库的**形态判定**本就只依赖 `lib/` 目录 + `edit`/`edit.bat` 脚本（`VaultDetector.isStandaloneRepo`），
  与 config.json 无关，因此移除它不会影响识别与启动。
- 明文偏好属于「用户使用习惯」，天然是**跨仓库/用户级**的，放到系统级配置更贴合语义。

## 遗留文件处理

已存在的独立仓库根 `config.json` 采取**只停用、不删除**：

- 应用不再读写它，也不在任何流程中删除它（含「降级为普通仓库」）；
- 由用户自行决定是否删除（它是 git 跟踪文件，可随时从历史取回）。

## 影响面

- `RepoCreator.createStandalone/upgradeToStandalone` 去掉配置参数，不再写 config.json；
  `downgradeToNormal` 不再删除 config.json；`refreshStandaloneRuntime` 保持不触碰它。
- `VaultDetector` 移除仓库级配置读写 API（`configFile`/`loadRepoConfig`/`writeRepoConfig`）。
- `UserConfig` 移除「按目录构造」，只剩系统级单例构造；移除 `raw()`。
- 分发装配（`distribution.xml`）与 `src/main/resources/config.json` 模板（无代码读取）一并移除。
