# 06 Git 同步

> 同步是**可选**能力，仅当库目录命中"在 git 仓库内且基本只有 markdown 对象"时启用（见 04b"库形态与 Git 关系"）。否则管理器只读写存储，不操作 git；本文件描述启用同步时的行为。

## 选型

- Git 操作：本地 git 命令（`ProcessBuilder` 调系统 `git`，行为与用户本地 git 一致）。要求目标机器已装 git。
- SSH 传输：走系统 `ssh`，经 `GIT_SSH_COMMAND` 环境变量临时注入本次操作的私钥路径
  （`ssh -i <key> -o IdentitiesOnly=yes`），进程级生效、不写全局配置；多设备一致时也可走 ssh-agent / known_hosts。
- 不支持系统 git 的机器：同步为不可用（同步仍是可选能力，仅命中"完全托管"时启用）。

## 同步模型

- 仅当启用同步（见 04b"库形态与 Git 关系"）时：每次 ChangeSet 提交自动生成一个 commit（作者信息可配置，如 `sanctum <local>`）；缺 `.git` 则用 `git init`。
- **完全托管仓库判定**：库根**全部是 markdown 文件**即视为完全托管——包括无块的（纯用户正文）和用户手动改动的文件，全部纳入 git 提交与同步（见 04b）。满足才提供**同步按钮**。
- 同步前需解锁（DEK 在内存），同步本身不碰明文。
- 未启用同步时：改动直接落盘，无 commit / push / pull。

## 同步流程（自动，不依赖用户介入）

**同步按钮**（仅完全托管仓库提供）触发以下确定步骤。支持**多个远端**（方案 B：每个远端都既 pull 又 push，平级对待）：

```
对每个远端 remote 依次执行：
  1. fetch：拉取该远端
  2. pull --rebase：将该远端历史 rebase 到本地
  3. 冲突自动解决（见"冲突策略"）
  然后 push 到全部远端
```

完整确定步骤：
```
1. 更新 manifest（写入最新 warehouseTime，重算 MAC）——见 02"仓库时间戳"
2. commit：本次全部改动（含 manifest）一次提交
3. 对每个远端：fetch + pull --rebase + 冲突自动解决
4. push 到全部远端
```

- **多远端平级**：所有远端都是 fetch/pull/push 目标，无主备之分。远端历史若分叉，rebase 到每个远端各自解决；所有远端都同步到同一最终状态。
- 全程自动，无用户选择；无法自动解决者记入 `.conflict` 供下次打开核查，但不阻塞同步。
- 定时/启动时也可自动执行同一流程。

## 冲突策略（自动）

冲突发生在**同一文件被两端同时修改**时（git 文件级冲突）；不同文件/不同区域的修改 git 自动合并。

按对象类型自动处理（无需用户介入）：

1. **不同对象 / 不同块**：git 自动合并（不同字段对象 = 不同文件或不同区域 → 自动合并）。
2. **同一对象（同一块）被两端改** → 按 `updateTimestamp` 仲裁：**大者 wins**（保留 updateTimestamp 较大的一侧），被覆盖方版本复制到 `<文件名>.conflict` 供核查，不阻塞。
3. **同 `updateTimestamp`（并发无先后）**：保留本地版本，远端版本记入 `.conflict`，下次打开提示待核查。
4. **无块的用户正文文件冲突**：先让 git 三方合并（文本合并）；无法自动合并的同一区域冲突 → 保留本地，远端版本复制为 `<文件名>.conflict`。
- 冲突解决后直接 commit，无重签、无 trusted.log（见"提交"）。
- `.conflict` 文件落于库根（随仓库版本化），下次打开时 UI 提示待核查，但不影响自动同步。
- 远端（GitHub / 自建 Git 服务）即备份。

## 冲突获取与自动解决机制

**获取冲突**（本地 git）：
1. `git status --porcelain` → 过滤未合并冲突项（状态码 `UU`）得到冲突文件路径清单。
2. 对每个冲突路径，用 `git show :2:<path>`（ours/本地）与 `git show :3:<path>`（theirs/远端）读出两版内容。
   - 注：rebase 与 merge 的 ours/theirs 方向相反，故**仲裁不依赖 ours/theirs 语义**，一律以块时间戳为准。

**自动解决**（按对象类型）：
1. **独立对象文件**（一个文件一个块）：对 ours/theirs 各自解析块前缀 `timestamp:base58` 的冒号前数字（无需解密）；**大者 wins**，写回该文件、`git add` 标记 resolved。
2. **共享/无块正文文件**：先让 git 三方合并（文本合并）；同区域无法合并 → 保留本地（ours），远端版本复制为 `<文件名>.conflict`。
3. 全部 resolved 后 `rebase --continue`，继续下一步/下一远端。

**仲裁原则**：一律按块时间戳判定，不依赖 git 的 ours/theirs 方向，避免 rebase/merge 语义差异导致的误判；时间戳同值（并发无先后）→ 保留本地 + 对方记 `.conflict`。时间戳为块前缀明文，解析无需解密，因此本地 git 即可完成仲裁。

## 远端配置与凭据存放

- **远端列表（名称、URL、key 引用）**：存为 vault 内 SECRET 对象（随机 UUID，内容自描述，见 05），解锁后扫描定位并应用到 git 远端——多设备一致，无手动重复配置；URL 会泄露托管商，故不落明文。key 引用指向 vault 内密钥 UUID 或系统 key 名。**支持多个远端**，同步时逐个 fetch/pull/push（方案 B，见"同步流程"）。
- **SSH 私钥默认存 vault 内**（SECRET 对象，key = `<密钥UUID>`，见 05）：与条目同级加密保护，解锁后在内存持有，经 `GIT_SSH_COMMAND` 临时拼 `ssh -i <临时key文件> -o IdentitiesOnly=yes` 注入本次 git 调用（临时 key 文件用完即删、锁定即弃），**不落明文盘**。主密码即所有凭据的单点，需保持强口令并做好归档备份。
- 备选：系统 ssh-agent / OS keychain（远端配置的 key 提示指向系统 key 或 vault 内密钥 UUID，二选一）。
- known_hosts 由系统 ssh 按标准位置（`~/.ssh/known_hosts`）管理，不随 vault 同步。
- HTTPS 备用传输的 token 同样放 OS keychain，不入 vault。

## 提交

- 每个 ChangeSet 提交自动生成一个 commit，使用本地 git 提交（作者信息可配置，如 `sanctum <local>`）。
- **不做任何 commit 消息加密或校验**：commit 消息只作人类可读说明，不含 n/l/m 脚注，也没有 change.log / trusted.log。篡改历史、回滚、变基、压平都交给 git 原生语义，应用不参与重签或信任判定。
- 对象内容完整性由**块级认证加密**保证（见 02：GCM-SIV tag + AAD 绑定 uuid），提交消息无需绑定文件内容。
- 审计"某次改了哪些文件"直接用 `git log --stat` / diff 查看，不依赖额外日志文件。

## 关联文档

- 仓库文件布局：04 存储设计
- SSH 密钥生成涉及 Bouncy Castle：02 加密设计
