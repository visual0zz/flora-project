# 04 Git 同步

## 选型

- Git 操作：JGit（纯 Java，程序化 commit/push/pull，无需系统安装 git）。
- SSH 传输：`org.eclipse.jgit.ssh.apache`（基于 Apache MINA SSHD），JGit 官方维护，支持：
  - Ed25519 / RSA 密钥；
  - ssh-agent；
  - known_hosts 管理。
- 不使用 JSch（年久失修）。
- 备选传输：HTTPS（可配 token）。

## 同步模型

- 每次写入操作后自动 commit（作者信息可配置，如 `sanctum <local>`）。
- 手动 / 定时 / 启动时执行 pull --rebase + push。
- 同步前需解锁（DEK 在内存），同步本身不碰明文。

## 冲突策略

- 字段级文件天然减少冲突；仍冲突时（同字段双端修改）：
  1. 默认 last-write-wins：保留本地，对方版本存入 `.conflict/<uuid>/<field>`；
  2. UI 中展示冲突条目，供用户选择保留哪份。
- 远端（GitHub / 自建 Git 服务）即备份。

## 关联文档

- 仓库文件布局：03 存储设计
- SSH 密钥生成涉及 Bouncy Castle：02 加密设计
