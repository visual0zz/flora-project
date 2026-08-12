# 06 Git 同步

## 选型

- Git 操作：JGit（纯 Java，程序化 commit/push/pull，无需系统安装 git）。
- SSH 传输：`org.eclipse.jgit.ssh.apache`（基于 Apache MINA SSHD），JGit 官方维护，支持：
  - Ed25519 / RSA 密钥；
  - ssh-agent；
  - known_hosts 管理。
- 不使用 JSch（年久失修）。
- 备选传输：HTTPS（可配 token）。

## 同步模型

- 每次 ChangeSet 提交自动生成一个 commit（作者信息可配置，如 `sanctum <local>`）。
- 手动 / 定时 / 启动时执行 pull --rebase + push。
- 同步前需解锁（DEK 在内存），同步本身不碰明文。

## 冲突策略

- 冲突发生在**同一 key 被两端同时修改**时（git 文件级冲突，如同一条目被两端同时修改）；不同 key 的并发修改天然可合并。
- 冲突处理（层级）：
  1. **字段值对象 = 文件级**：两端改不同字段 → 不同字段对象 → git 自动合并；改同一字段对象 → 冲突，last-write-wins / 用户选择，无需三方合并（见 05）。
  2. **条目记录冲突（低频）**：仅当两端同时改元数据或字段映射时发生 → 应用层三方合并（merge-base 祖先 + 按字段名合并映射；单侧修改自动采用、双侧改同一字段名 → 冲突，UI 选择或 last-write-wins 记入 `.conflict`）。合并结果重签，change.log 记 `M`，merge commit 重签。
  3. 非条目对象或不做合并：默认 last-write-wins，对方版本记入 `.conflict` 清单供核查；
  4. UI 展示冲突条目，供用户选择保留哪份。
- `change.log` 本身是纯追加：两侧追加 union 即可（重复条目无害，内容哈希列可区分版本），git 通常自动合并；冲突标记由应用按 union 归并后重签。
- 远端（GitHub / 自建 Git 服务）即备份。

## 远端配置与凭据存放

- **远端列表（名称、URL、key 引用）**：存为 vault 内 SECRET 对象（随机 UUID，内容自描述，见 05），解锁后扫描定位并应用到 jgit 配置——多设备一致，无手动重复配置；URL 会泄露托管商，故不落明文。key 引用指向 vault 内密钥 UUID 或系统 key 名。
- **SSH 私钥默认存 vault 内**（SECRET 对象，key = `secret/<密钥UUID>`，见 05）：与条目同级加密保护，解锁后在内存持有，经 sshd 的 KeyProvider 直接提供 jgit 使用（或可选暴露本地 ssh-agent 供外部工具），**不落明文盘、锁定即弃**。主密码即所有凭据的单点，需保持强口令并做好归档备份。
- 备选：系统 ssh-agent / OS keychain（远端配置的 key 提示指向系统 key 或 vault 内密钥 UUID，二选一）。
- known_hosts 由 jgit 按标准位置（`~/.ssh/known_hosts`）管理，不随 vault 同步。
- HTTPS 备用传输的 token 同样放 OS keychain，不入 vault。

## 提交消息认证

- 仓库根维护明文日志 `change.log`（路径只含 UUID，无敏感信息），每次提交追加本次改动行：`A <path>`（新增）/ `M <path>`（修改）/ `D <path>`（删除）。

  ```
  A secret/550e8400-e29b-41d4-a716-446655440000
  M secret/f47ac10b-58cc-4372-a567-0e02b2c3d479
  D secret/c81e728d-9d4c-4b3f-9a6e-3c1f8b2a0d5e
  M plain/0b2c3d4e-5f6a-7b8c-9d0e-1f2a3b4c5d6e  9f86d081884c7d65…（可选内容哈希）
  ```

  - 纯追加、无 commit 分隔标记：commit 边界由 git 版本化天然提供（相邻 commit 的 log 差集即当次改动）；编码固定 UTF-8 + LF，`l` 按文件原始字节哈希；未来压缩旧行随一次 commit 发生，哈希照常衔接。
- commit 消息写入**单行自包含**脚注（不依赖 git 内部字段）：

  ```
  sanctum-v1 n=42 l=<64hex> m=<64hex>
  ```

  - `n`：单调计数器，提交时取 `max(所有父 commit 的 n) + 1`——沿**任意路径**严格递增且全图唯一（合并/压平后跳变允许，重排乱序仍被非递增抓出）；
  - `l` = `change.log` 当前内容的 SHA-256——消息因此绑定"本次改了哪些文件"；
  - `m = HMAC-SHA256(signKey, "sanctum-commit"‖n‖l)`，signKey 由 HKDF(DEK) 派生（见 02）。m 只绑 n‖l、**不绑 commit 对象**——rebase 保留消息、重定父不影响 m。
- log 文件由 git 自身版本化：审计"某次改了哪些文件"直接读任意历史 commit 的 change.log 即可。
- **校验**（pull / clone 后，按 DAG 遍历）：
  1. 每个 commit：m 有效（无 DEK 无法构造任何合法 commit）；
  2. n 沿任意路径严格递增且全图唯一（复制现有脚注必违反其一；并发分叉撞号由应用在 pull --rebase 时重签一侧解决）；
  3. 逐 commit 读其树中的 `change.log`，内容 sha256 与消息 l 一致——log 被篡改/替换即暴露（JGit 读单个文件，公开 API）。
- **容忍历史合并/变基/压平**：合并与压平后 change.log 取两侧条目的并集，由**应用**归并并重签；历史改写（merge/rebase/squash）须经应用执行，裸 `git rebase` 不在支持范围（校验会定位到不符的 commit 并提示修复）。
- **双机分叉 merge 场景**：change.log 纯追加设计使 git 对两侧追加通常自动合并（无冲突）。merge 由应用执行 → 正常重签（n = max(两父 n)+1，log 取并集）直接通过；裸 `git merge` 产生的 merge commit 无合法脚注 → 若为 HEAD 且无后代，应用可 amend 补签（无级联，注意会改变该 commit 哈希）；否则走信任机制（见下）。
- **可选增强**：行格式加内容哈希（`M secret/xxx <64hex>`），绑定文件内容（防同 key 换成旧内容），代价每行 +70B。
- 边界：不防整体回滚（旧脚注合法）；可选本地 last-seen 锚点分辨撞号原版并检出回滚。

## 手工提交的信任机制

适配"手工操作 git 产生坏 commit 但内容想保留"的场景：

- 仓库根维护明文 `trusted.log`（git 版本化）：每行一个被信任 commit 的完整哈希，可带注解（`# 日期 原因`）。
- 信任操作由应用提供 `trust <commit>`：写入 trusted.log 并作为一次正常提交（带合法脚注）；修改 trusted.log 本身作为文件改动进入 change.log（`M trusted.log`），信任变更留在认证链上。
- 校验规则：遇到坏 commit（m 无效 / n 冲突 / l 不匹配）时查 trusted.log——命中则跳过该 commit 的单项校验，且 **n 基线不更新**（信任段对后续校验透明：其后第一个合法 commit 只需比最近一个未信任 commit 的 n 大）。
- 安全：攻击者无 DEK 无法新增信任条目；应用在信任前提示确认，防止误信攻击者注入的坏 commit。
- 边界：被信任的 commit 及其内容脱离自动校验，由用户自负；仅"校验失败"的 commit 需要信任。

## 关联文档

- 仓库文件布局：04 存储设计
- SSH 密钥生成涉及 Bouncy Castle：02 加密设计
