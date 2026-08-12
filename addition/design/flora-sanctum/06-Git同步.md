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
- 冲突处理：
  1. 默认 last-write-wins：保留本地版本，对方版本记入 `.conflict` 清单供核查；
  2. UI 中展示冲突条目，供用户选择保留哪份。
- 远端（GitHub / 自建 Git 服务）即备份。

## 关联文档

- 仓库文件布局：04 存储设计
- SSH 密钥生成涉及 Bouncy Castle：02 加密设计
