# flora-sanctum 密码管理器设计方案

## 1. 目标与核心思路

- 纯 Java（Java 26 / JPMS）多模块，作为 flora-project 下的新模块 `flora-sanctum`。
- 密码库 = 一个 Git 仓库，工作区里是一堆散碎的密文文件。
- 加密用 Bouncy Castle，Git 操作用 JGit。
- 关键性质：**明文的局部修改只引起密文的局部修改**，使得：
  - Git diff / 提交粒度小，同步流量小；
  - 不同条目（或不同字段）的并发修改在 Git 层面天然可合并，减少冲突；
  - 远端（GitHub / 自建 Git 服务）本身就是备份。

## 2. 模块划分

```
flora-sanctum/
├── flora-sanctum-core/      -- 纯 Java，无 UI 依赖
│   ├── crypto/              -- 加密、密钥派生、TOTP（Bouncy Castle）
│   ├── model/               -- 条目模型（公开 API）
│   ├── store/               -- 密文文件读写、仓库格式
│   └── sync/                -- JGit 封装：commit / push / pull / 冲突处理
├── flora-sanctum-cli/       -- 命令行入口（可选，利于测试与脚本化）
└── flora-sanctum-ui/        -- JavaFX 桌面界面，依赖 core
```

- core 的 `module-info.java` 只导出 `com.flora.sanctum.{model,crypto,store,sync}` 等公开包，内部实现放 `impl` 子包。
- 使用 flora-root 的 tag 注解标注语义（如线程安全、数据敏感性）。

## 3. 存储与加密设计

### 3.1 粒度取舍

| 方案 | 优点 | 缺点 |
|---|---|---|
| 每条目一个加密文件 | 文件少，简单 | 改一个字段重写整个文件，并发改同条目冲突 |
| 每字段一个加密文件（目录式） | 最贴合"局部修改局部密文"，git 合并友好 | 文件多（每条目约 5~10 个） |

**推荐：目录式，每条目一个目录、每字段一个文件。**

```
vault/                      -- Git 仓库根
├── index.json              -- 可选明文索引（条目名 → 目录，便于列表不解密）
└── entries/
    └── <entry-uuid>/
        ├── meta            -- 加密的 JSON：字段名清单、自定义字段
        ├── password        -- 加密的字段值
        ├── username
        ├── url
        ├── totp            -- 加密的 TOTP 种子
        └── notes
```

- 修改任一字段只重写该字段文件，Git 只看到一个文件变化。
- 不敏感信息（条目名、修改时间）可放在 `index.json` 明文，换取列表渲染不解密；若条目名也敏感，则索引整体加密，每次解锁重建。
- 文件名不含明文信息（UUID），目录结构不泄露内容。

### 3.2 密钥层次

```
master password
   └─ Argon2id (BC 的 Argon2BytesGenerator) ─> KEK (256 bit)
        └─ 随机生成并加密存储的 DEK 文件 (vault/key.bin)
             ├─ AES-256-GCM 加密每个字段文件
             └─ DEK 每次打开仓库后仅存于内存
```

- 每个字段文件格式：`magic(4B) + version(1B) + nonce(12B) + ciphertext + tag(16B)`，AES-GCM 自带认证。
- **换主密码只重写 `key.bin`**，所有条目文件不动，完美保持"局部修改"性质。
- Argon2id 建议参数（可调，存入文件头以便将来升级）：memory 64 MiB、iterations 3、parallelism 4。
- 字段值为明文 UTF-8 序列化（JSON 或直接字符串），无需二次编码。

### 3.3 防降级与完整性

- 文件头带 magic + version，读取时校验；版本不符拒绝打开，防止降级攻击。
- 仓库根放 `vault/manifest`（记录格式版本、KDF 参数），打开时先校验。

## 4. Git 同步（JGit）

### 4.1 选型

- **JGit**：纯 Java 的 Git 实现，程序化 commit/push/pull，无需系统安装 git。
- **SSH 传输：`org.eclipse.jgit.ssh.apache`**（基于 Apache MINA SSHD），这是 JGit 官方维护的 SSH 后端，支持：
  - Ed25519 / RSA 密钥（可由 BC 生成与解析）；
  - ssh-agent（通过 MINA SSHD 的 agent 支持）；
  - known_hosts 管理。
- 不建议 JSch（年久失修，与 JGit 集成是第三方维护）。
- 远端除 SSH 外，JGit 也支持 HTTPS（可配 token），可作为备选传输。

### 4.2 同步模型

- 每次写入操作后自动 commit（作者信息可配置，如 `sanctum <local>`）。
- 手动/定时/启动时 pull --rebase + push。
- **冲突策略**：字段级文件天然减少冲突；仍冲突时（同字段双端修改）：
  1. 默认 last-write-wins（保留本地，把对方版本存入 `.conflict/<uuid>/<field>` 待用户处置）；
  2. 高级版：在 UI 中展示冲突条目让用户选择保留哪份。
- 同步前需解锁（DEK 在内存），同步本身不碰明文。

## 5. UI 选型

**推荐 JavaFX**（本项目是纯 Java + JPMS）：

- 与现有技术栈一致，JPMS 下 module-path 运行成熟；
- 无额外语言（Compose Multiplatform 会引入 Kotlin）；
- 密码管理器需要的桌面能力 JavaFX + AWT 都能覆盖：
  - 系统托盘（AWT SystemTray）、全局快捷键（jnativehook，可选）、
  - 剪贴板写入 + 定时清空（Toolkit）、自动锁定定时器、TOTP 倒计时。
- UI 只调用 core 的公开 API，不解密、不碰 Git，便于未来增加 CLI / Web 端。

备选：JetBrains Compose Multiplatform Desktop（声明式、界面更现代），代价是引入 Kotlin 与新的构建配置，若团队接受 Kotlin 可考虑。

## 6. 安全要点

- 明文不用 `String` 承载密码：用 `byte[]`/`char[]`，用后 wipe（BC 提供 `Arrays` 工具或手写 fill(0)），记录在模型注解中。
- 剪贴板自动清空（默认 30s）。
- 不活动自动锁定（默认 5 分钟）：丢弃 KEK/DEK，再次操作需主密码。
- 内存中不落盘临时明文文件。
- 备份即远端：鼓励多远端（`push.cmd` 模式可复用），另提供导出加密归档命令。
- 日志中禁止出现明文与主密码。

## 7. 依赖清单（版本以当时最新稳定为准）

| 用途 | 坐标 |
|---|---|
| 加密 | `org.bouncycastle:bcprov-jdk18on`（+ `bcpkix-jdk18on` 如需证书） |
| Git | `org.eclipse.jgit:org.eclipse.jgit` |
| SSH | `org.eclipse.jgit:org.eclipse.jgit.ssh.apache`（传递引入 MINA SSHD） |
| UI | `org.openjfx:javafx-controls` / `javafx-fxml`（platform classifier） |
| 测试 | JUnit 5 + `@Tag("slow")`（遵循项目 test.cmd / test-slow.cmd 分工） |

## 8. 实施路线

1. **core-crypto**：Argon2id 派生、AES-GCM 字段文件格式、key.bin 管理、单元测试（含 KAT 向量）。
2. **core-store**：条目模型、目录式仓库读写、index.json；验证"改一字段只动一文件"。
3. **core-sync**：JGit 封装（init/commit/pull/push/冲突收集），SSH 密钥与 known_hosts 管理。
4. **cli**：`add/get/list/sync` 命令，作为 core 的冒烟测试。
5. **ui**：JavaFX 主界面（列表 + 编辑 + 解锁屏 + 托盘），自动锁定与剪贴板清空。
6. 对照 AGENTS.md：补 CHANGELOG、`addition/decision/` 决策记录、tag 注解标注。
