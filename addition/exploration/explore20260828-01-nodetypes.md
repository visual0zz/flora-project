# Sanctum 节点类型与内部 JSON 字段总览（2026-08-28）

> ⚠️ **本文已过时**——本快照捕捉于 2026-08-28，部分内容已被后续重构推翻。存储 / 字段格式以 **04-存储设计.md**、**05-密码库适配器.md** 为准；加密设计以 **02-加密设计.md** 为准。
>
> 与现行实现的主要差异（非穷举）：
> - 信封头已不含对象 uuid（uuid 改由存储路径承载）；块内时间戳编码由 base58 改为 base64，且不再额外异或混淆。
> - 字段块统一为 `type=field`（随机 uuid + parent=条目），预设 / 自定义语义仅由 `name` 是否在 `PRESET_NAMES` 区分；旧版 `predefField` / `customField` type 仅作读端兼容别名。本文仍以 `predefField` / `customField` 两种 type 描述，已不准确。
> - 预设字段名为 `password / url / username / labels / notes`；`createTime` / `updateTime` **不是字段块**，而是内联在条目 JSON 内（只读）。本文误将其列为预设字段块。
> - 根对象与每个 group 均持 `dek1 / dek2` 双 DEK（明文 base64），非单一 `dek`；本文描述为单 DEK + 包裹，已过时。
> - manifest 字段为 `crypto`（算法套件）而非 `cryptoVersion`，且已无 `updateTimestamp` 字段。

本文档统计 `flora-sanctum-core` 当前所有**存储节点类型**（`StoredNodeType`），
逐个说明其结构、功能、加密路由与**块内 JSON 字段**。
阅读对象：需要理解仓库磁盘结构的开发者（GC、同步、备份、格式兼容均依赖此）。

源码位置：`flora-sanctum/flora-sanctum-core/src/main/java/com/flora/sanctum/model/`
（`StoredNodeType.java`、`model/tree/*`、`model/vault/*`、`model/impl/TreeContext.java`）。

---

## 一、存储模型基础

### 1.1 块（Block）= 一个 JSON 对象 + 信封

- 仓库落盘单位为**块**；每个块 = 一个加密信封（magic + 对象 uuid + flags + 密文 + MAC）。
- 信封明文负载是一段 **JSON**（见各节点字段表）。JSON 内**不含** `uuid` 与时间戳——
  二者在信封头与块前缀中：
  - `uuid`：写死在信封头（固定 16 字节偏移），亦决定 git 风格分片路径 `{前2字符}/{后30字符}.md`。
  - 时间戳：写死在 `.md` 文件单行前缀 `timestamp:base58`，用于冲突仲裁与时钟锚点。
- `type` 字段：**持久化在 JSON 内**，是节点的身份标识（见 `StoredNodeType.tag()`）。

### 1.2 磁盘文件格式（`MarkdownObjectStore`）

- 路径：`{库根}/{xx}/{yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy}.md`（xx = uuid 去连字符前 2 字符）。
- 内容：单行 `timestamp:base58`（base58 串解码 + 解异或 = 完整信封字节）。
- 一个文件 = 一个块；覆盖写用同目录 `.tmp` 原子替换，不留半写块。

### 1.3 加密路由（谁用哪个 DEK）

| 节点 | 加密密钥 |
|---|---|
| `root` | KEK（主密码经 Argon2id 派生）包裹 |
| `group` | 自身 DEK，由其父 DEK（父组 DEK 或 data 根 DEK）包裹 |
| `entry` / `predefField` / `customField` | 所在组 DEK；顶层条目用 data 根 DEK |
| `icon` / `sshKey` / `remote` / `config` | data 根 DEK |

`TreeContext.dekFor(groupId)` 实现："条目/字段若在子 group 下用该 group DEK，否则用 data 根"。

### 1.4 展示归属（`ViewNodeType`，不持久化）

UI 区段标记，与存储类型解耦。各存储类型的 `view()` 声明其归属：
PASSWORD（密码库）/ ICON（图标）/ SSH_KEY（SSH 密钥）/ REMOTE（远程）/ SETTINGS（设置）。
`TRASH` 为垃圾桶虚拟根（与数据根平级），`ROOT` 无展示归属。

---

## 二、节点类型清单（共 10 种）

> 字段表说明：`type` 为 JSON 内持久化字符串；`parent` 为父对象 uuid 字符串
> （顶层指向 root 对象 uuid）；其余为业务字段。所有字符串值均为明文 JSON，落盘时整体进信封加密。

### 2.1 MANIFEST — 引导块（type=`manifest`）

| 项 | 内容 |
|---|---|
| 功能 | 明文引导块：记录 KDF 参数与根对象 uuid，解锁时 O(1) 定位；带 HMAC-SHA256（MAC 在块尾，不在 JSON 内） |
| 固定 uuid | `00000000-0000-0000-0000-000000000000`（全 0 保留，分片路径固定 `00/0000…00.md`） |
| 展示归属 | SETTINGS（不显示为普通对象） |
| 加密 | **明文块**（非密文，但有 MAC 校验） |
| parent | 无 |

**JSON 字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `version` | int | 格式版本（当前 1） |
| `type` | string | `"manifest"` |
| `cryptoVersion` | string | 加密版本（当前 `"gcm-siv-1"`） |
| `kdf` | string | KDF 算法（`"argon2id"`） |
| `salt` | string | Base64 盐（16 字节） |
| `params` | object | KDF 强度：`{memoryKiB, iterations, parallelism}` |
| `rootObjectUuid` | string | 根对象（data 根）uuid（旧库兼容键名 `rootGroupUuid`） |
| `updateTimestamp` | long | 仓库更新时间戳（默认 1） |

---

### 2.2 ROOT — 数据根对象（type=`root`）

| 项 | 内容 |
|---|---|
| 功能 | 唯一根对象，持有**根 DEK**（经 KEK 包裹）与**仓库级 keyId 种子**（`repoKeyIdSeed`，用于 keyId 防关联）；所有顶层节点的 parent 指向它 |
| 展示归属 | 无（基础设施，不暴露为节点） |
| 加密 | KEK 包裹（密文块） |
| parent | 无 |

**JSON 字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | `"root"` |
| `dek` | string | Base64：根 DEK 经 KEK（AES-GCM-SIV）包裹的结果 |
| `repoKeyIdSeed` | string | Base64：32 字节仓库级种子，解锁时读取，参与 repoKeyId 派生 |

---

### 2.3 GROUP — 文件夹（type=`group`）

| 项 | 内容 |
|---|---|
| 功能 | 组织条目的容器；每个文件夹绑定一个 DEK（子组 DEK 用父 DEK 包裹，实现"文件夹级"加密隔离）；支持新建子组/条目、重命名、递归删除、图标引用 |
| 展示归属 | PASSWORD |
| 加密 | 自身 DEK 由父 DEK 包裹；块体用父 DEK 加密 |
| parent | 父组 uuid；顶层为 root 对象 uuid |

**JSON 字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | `"group"` |
| `name` | string | 文件夹名 |
| `parent` | string | 父组 uuid / root 对象 uuid |
| `dek` | string | Base64：本组 DEK 经父 DEK 包裹 |
| `iconRef` | object? | 可选，图标引用（Ref 结构，见 §三） |

---

### 2.4 ENTRY — 密码条目（type=`entry`）

| 项 | 内容 |
|---|---|
| 功能 | 一条密码记录。内置预设字段与自定义字段**均不以 entry 内联字段存储**，而是作为 entry 的**独立子块**（保持"条目一切属性皆子块"） |
| 展示归属 | PASSWORD |
| 加密 | 所在组 DEK（顶层用 data 根 DEK） |
| parent | 所属组 uuid；顶层为 root 对象 uuid |

**JSON 字段（entry 自身仅这些）**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | `"entry"` |
| `name` | string | 条目名（如"微博"） |
| `parent` | string | 父组 uuid / root 对象 uuid |
| `iconRef` | object? | 可选，图标引用（Ref 结构） |

> 条目"内容"（密码/URL/用户名/标签/时间）不在 entry 块内，见 §2.5 / §2.6。

---

### 2.5 PREDEF_FIELD — 预设字段（type=`predefField`）

| 项 | 内容 |
|---|---|
| 功能 | 系统创建/管理的条目元数据，作为 entry 的**子块**独立存储。预设名固定集合：`password` / `url` / `username` / `labels` / `createTime` / `updateTime`（见 `EntryFields.PRESET_NAMES`） |
| 展示归属 | PASSWORD |
| 加密 | 同其所属 entry（组 DEK / data 根 DEK） |
| parent | 所属 entry 的 uuid |

**JSON 字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | `"predefField"` |
| `name` | string | 预设字段名（上述 6 个之一） |
| `value` | string | 字段值（`labels` 为逗号分隔串；`createTime`/`updateTime` 为本地毫秒字符串；空值不写块） |
| `parent` | string | 所属 entry 的 uuid |

> 注：`FieldNode.type()` 在代码中统一返回 `PREDEF_FIELD`，但**实际落盘 `type` 区分** `predefField` 与 `customField`；二者负载字段完全相同，刻意分两种 type 以便 GC/遍历按 type 直接过滤。

---

### 2.6 CUSTOM_FIELD — 自定义字段（type=`customField`）

| 项 | 内容 |
|---|---|
| 功能 | 用户创建的条目附加字段（如 `website`、`totp`）；结构同预设字段，但 type 为 `customField`，且**预设字段名不可用于自定义字段**（`EntryFields.isPreset` 拒绝） |
| 展示归属 | PASSWORD |
| 加密 | 同其所属 entry |
| parent | 所属 entry 的 uuid |

**JSON 字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | `"customField"` |
| `name` | string | 自定义字段名（不可为预设名） |
| `value` | string | 字段值（`kind=totp` 时值为 Base32 种子，可生成 6 位/30 秒验证码） |
| `kind` | string? | 可选，自由字符串（如 `"totp"`）；未知 kind 也可写入 |
| `parent` | string | 所属 entry 的 uuid |

---

### 2.7 CONFIG — 仓库级设置（type=`config`）

| 项 | 内容 |
|---|---|
| 功能 | 仓库级设置项（主题/自动锁定时长/剪贴板清空时长），**加密存储于 data 根下**，取代旧版全局配置文件；UI 不显示为普通对象 |
| 展示归属 | SETTINGS |
| 加密 | data 根 DEK |
| parent | root 对象 uuid |

**JSON 字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | `"config"` |
| `key` | string | 设置键（如 `theme` / `lockTimeoutSeconds` / `clipboardClearSeconds`） |
| `value` | string | 设置值（数值以字符串存） |
| `parent` | string | root 对象 uuid |

> 已知约定键：`theme`（默认 `"system"`）、`lockTimeoutSeconds`（默认 300）、`clipboardClearSeconds`（默认 30）。

---

### 2.8 ICON — 自定义图标（type=`icon`）

| 项 | 内容 |
|---|---|
| 功能 | 用户导入的图标资源；可被 group / entry 通过 `iconRef` 引用 |
| 展示归属 | ICON |
| 加密 | data 根 DEK |
| parent | root 对象 uuid |

**JSON 字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | `"icon"` |
| `name` | string? | 可选，导入文件名 |
| `data` | string | Base64 图标二进制（PNG 等） |
| `format` | string | 格式（如 `"png"`） |
| `parent` | string | root 对象 uuid |

---

### 2.9 SSH_KEY — SSH 私钥（type=`sshKey`）

| 项 | 内容 |
|---|---|
| 功能 | SSH 私钥（PEM 文本），可被 remote 通过 `keyRef` 引用 |
| 展示归属 | SSH_KEY |
| 加密 | data 根 DEK |
| parent | root 对象 uuid |

**JSON 字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | `"sshKey"` |
| `name` | string | 密钥名 |
| `value` | string | 私钥 PEM 文本（字段命名与 `FieldNode.value` 统一） |
| `parent` | string | root 对象 uuid |

---

### 2.10 REMOTE — 远程配置（type=`remote`）

| 项 | 内容 |
|---|---|
| 功能 | 远程仓库（如 Git）连接配置；可被条目/同步引用其 `keyRef` 指向的 SSH 密钥 |
| 展示归属 | REMOTE |
| 加密 | data 根 DEK |
| parent | root 对象 uuid |

**JSON 字段**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | `"remote"` |
| `name` | string | 远程名（如 `origin`） |
| `url` | string | 远程地址（如 `git@example.com:repo.git`） |
| `keyRef` | object? | 可选，引用的 SSH 密钥（Ref 结构，scheme=`node:key`） |
| `parent` | string | root 对象 uuid |

---

## 三、统一引用结构 `Ref`（iconRef / keyRef 的值）

引用被收口为单一结构，序列化为含两字段的 JSON 对象：

```json
{ "type": "scheme:kind", "id": "<目标标识>" }
```

- `node`：引用仓库内数据节点，`kind` = 被引用节点 type 标签（icon→`ICON`，key→`SSH_KEY`），`id` = 目标 uuid。
  - 例：`{"type":"node:icon","id":"<iconUuid>"}`、`{"type":"node:key","id":"<sshKeyUuid>"}`
- `builtin`：引用应用内置资源（不进对象库），`kind` 目前仅 `icon`，`id` = 资源名（如 `folder`）。
  - 例：`{"type":"builtin:icon","id":"folder"}`

解析兼容遗留格式：`"builtin:name"` 字符串或纯 uuid 串（按字段默认 kind：`iconRef`→`icon`、`keyRef`→`key`）。

---

## 四、节点类型总表（速查）

| type 标签 | StoredNodeType | 展示归属 | 加密密钥 | 主要 JSON 字段 | parent |
|---|---|---|---|---|---|
| `manifest` | MANIFEST | SETTINGS | 明文+MAC | version/cryptoVersion/kdf/salt/params/rootObjectUuid/updateTimestamp | — |
| `root` | ROOT | — | KEK | dek/repoKeyIdSeed | — |
| `group` | GROUP | PASSWORD | 父 DEK | type/name/parent/dek/iconRef? | 父组/root |
| `entry` | ENTRY | PASSWORD | 组或根 DEK | type/name/parent/iconRef? | 父组/root |
| `predefField` | PREDEF_FIELD | PASSWORD | 同 entry | type/name/value/parent | entry uuid |
| `customField` | CUSTOM_FIELD | PASSWORD | 同 entry | type/name/value/kind?/parent | entry uuid |
| `config` | CONFIG | SETTINGS | data 根 DEK | type/key/value/parent | root uuid |
| `icon` | ICON | ICON | data 根 DEK | type/name?/data/format/parent | root uuid |
| `sshKey` | SSH_KEY | SSH_KEY | data 根 DEK | type/name/value/parent | root uuid |
| `remote` | REMOTE | REMOTE | data 根 DEK | type/name/url/keyRef?/parent | root uuid |

---

## 五、关键不变式（兼容性提示）

- `StoredNodeType` 的 `tag` 值**持久化进块**，不可随意增删（旧库兼容）；新增类型需评估迁移。
- `uuid` 不在 JSON 内，仅在信封头与文件路径；任何"按 uuid 解析"都必须走信封/TreeContext，不能读 JSON。
- 顶层节点 `parent` 一律指向 root 对象 uuid（由 `Vault.rootObjectUuid()` 给出），不是字面字符串。
- `predefField` 与 `customField` 负载字段完全一致，区分仅靠 `type`；GC/遍历按 type 过滤，不依赖 name。
- `root` 与 `manifest` 为基础设施块，UI 与业务遍历（ObjectTree/IconTree/…）均不将其暴露为普通节点。
