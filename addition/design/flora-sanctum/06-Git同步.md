# 06 Git 同步

> 同步是**可选**能力，仅当库目录命中"在 git 仓库内且基本只有 markdown 对象"时启用（见 04b"库形态与 Git 关系"）。否则管理器只读写存储，不操作 git；本文件描述启用同步时的行为。

## 选型

- Git 操作：JGit（纯 Java，程序化 commit/push/pull，无需系统安装 git）。
- SSH 传输：`org.eclipse.jgit.ssh.apache`（基于 Apache MINA SSHD），JGit 官方维护，支持：
  - Ed25519 / RSA 密钥；
  - ssh-agent；
  - known_hosts 管理。
- 不使用 JSch（年久失修）。
- 备选传输：HTTPS（可配 token）。

## 同步模型

- 仅当启用同步（见 04b"库形态与 Git 关系"）时：每次 ChangeSet 提交自动生成一个 commit（作者信息可配置，如 `sanctum <local>`）；缺 `.git` 则用 JGit init。
- 手动 / 定时 / 启动时执行 pull --rebase + push。
- 同步前需解锁（DEK 在内存），同步本身不碰明文。
- 未启用同步时：改动直接落盘，无 commit / push / pull。

## 冲突策略

- 冲突发生在**同一 key 被两端同时修改**时（git 文件级冲突，如同一条目被两端同时修改）；不同 key 的并发修改天然可合并。
- 冲突处理（层级）：
  1. **字段值对象 = 文件级**：两端改不同字段 → 不同字段对象 → git 自动合并；改同一字段对象 → 冲突，last-write-wins / 用户选择，无需三方合并（见 05）。
  2. **条目记录冲突（低频）**：仅当两端同时改元数据或字段映射时发生 → 应用层三方合并（merge-base 祖先 + 按字段名合并映射；单侧修改自动采用、双侧改同一字段名 → 冲突，UI 选择或 last-write-wins 记入 `.conflict`）。
  3. 非条目对象或不做合并：默认 last-write-wins，对方版本记入 `.conflict` 清单供核查；
  4. UI 展示冲突条目，供用户选择保留哪份。
- 远端（GitHub / 自建 Git 服务）即备份。

## 远端配置与凭据存放

- **远端列表（名称、URL、key 引用）**：存为 vault 内 SECRET 对象（随机 UUID，内容自描述，见 05），解锁后扫描定位并应用到 jgit 配置——多设备一致，无手动重复配置；URL 会泄露托管商，故不落明文。key 引用指向 vault 内密钥 UUID 或系统 key 名。
- **SSH 私钥默认存 vault 内**（SECRET 对象，key = `<密钥UUID>`，见 05）：与条目同级加密保护，解锁后在内存持有，经 sshd 的 KeyProvider 直接提供 jgit 使用（或可选暴露本地 ssh-agent 供外部工具），**不落明文盘、锁定即弃**。主密码即所有凭据的单点，需保持强口令并做好归档备份。
- 备选：系统 ssh-agent / OS keychain（远端配置的 key 提示指向系统 key 或 vault 内密钥 UUID，二选一）。
- known_hosts 由 jgit 按标准位置（`~/.ssh/known_hosts`）管理，不随 vault 同步。
- HTTPS 备用传输的 token 同样放 OS keychain，不入 vault。

## 提交

- 每个 ChangeSet 提交自动生成一个 commit，使用普通 JGit 提交（作者信息可配置，如 `sanctum <local>`）。
- **不做任何 commit 消息加密或校验**：commit 消息只作人类可读说明，不含 n/l/m 脚注，也没有 change.log / trusted.log。篡改历史、回滚、变基、压平都交给 git 原生语义，应用不参与重签或信任判定。
- 对象内容完整性由**块级认证加密**保证（见 02：GCM-SIV tag + AAD 绑定 uuid），提交消息无需绑定文件内容。
- 审计"某次改了哪些文件"直接用 `git log --stat` / diff 查看，不依赖额外日志文件。

## 关联文档

- 仓库文件布局：04 存储设计
- SSH 密钥生成涉及 Bouncy Castle：02 加密设计
