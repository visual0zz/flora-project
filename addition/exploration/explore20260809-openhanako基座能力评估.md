# openhanako 基座能力评估：flora-root 需要新增的通用能力

> 日期：2026-08-09
> 范围：基于 `absent/otherprojects/openhanako`（Node.js/TS 个人 AI Agent）分析，评估"若用 Java 复刻，哪些通用算法/结构应放进 flora-root（零依赖工具库）"
> 判定标准：能力可被**某一类应用**复用，而非 openhanako 专属

---

## 一、openhanako 功能构成

OpenHanako 是一个有记忆、有性格、可自主行动的私人 AI 助理（Electron 桌面应用）。功能模块：

| 功能 | 说明 | 对应目录 |
|---|---|---|
| 记忆 | 近期事件清晰、旧忆自然淡忘 | `lib/memory/` |
| 人格 | 模板塑造说话方式，Agent=文件夹 | `lib/identity-templates/`、`lib/ishiki-templates/` |
| 工具 | 文件读写、终端命令、网页、搜索、截图、画布 | `lib/tools/` |
| 技能 | 社区 Skills 生态 + 自编技能 | `skills2set/` |
| 多 Agent | 独立记忆/人格/定时任务，频道协作、委派 | `hub/`、`core/` |
| 书桌 | 文件 + 笺（Jian）的异步协作空间 | `lib/desk/` |
| 定时任务与心跳 | Cron 定时 + 书桌文件变化巡检 | `lib/desk/cron-*.js`、`heartbeat.js` |
| 安全沙盒 | PathGuard 四级访问 + OS 级（Seatbelt/Bubblewrap） | `lib/sandbox/` |
| 多平台桥接 | Telegram / 飞书 / QQ 同时接入 | `lib/bridge/`、`lib/channels/` |

---

## 二、flora-root 已有能力对照（避免重复建议）

| 能力 | flora-root 位置 | 覆盖情况 |
|---|---|---|
| LLM 多 provider 调用 | `com.flora.ai.api`（ChatClient/StreamingClient/JsonClient/ToolCall/ToolSpec/TokenUsage/InferenceConfig + OpenAI/Anthropic/Gemini/DeepSeek provider） | ✅ 已覆盖 openhanako 的模型管理、多 provider 切换 |
| JSON 解析/构建/路径 | `com.flora.codec.json`（JsonParser/JsonBuilder/JsonPath） | ✅ |
| YAML/TOML/props/jsonl | `com.flora.codec.*` | ✅ |
| 配置加载 | `com.flora.runtime.config`（Config/Source/Priority/ConfigUtil） | ✅ 深度合并可复用 |
| 有界缓存 + 淘汰策略 | `com.flora.cache`（Bounded/LRU/LFU/FIFO/WTinyLfu） | ✅ |
| 解析器 | `com.flora.syntax.peg` | ✅ 可作流式解析的底座 |
| 虚拟文件系统 | `com.flora.runtime.virtual.filesys`（VFS/RealFileSystem/SymlinkFSBackend/MountTable） | ✅ 可作 PathGuard 底座 |
| 重试 | `com.flora.concurrent.retry` | ✅ |
| 哈希/ID/压缩 | `com.flora.entropy` | ✅ |
| 语义标注注解 | `com.flora.tag` | ✅ 新能力实现时应加标注 |

**flora-root 目前没有**：时间/业务日工具、cron 解析与调度、事件总线、标签化事实存储、路径权限判定、PII 脱敏、SSRF 防护、append-only 文件流存储、心跳指纹巡检。这些即本文评估要新增的部分。

---

## 三、需要新增的基座能力（按类别）

### A. 时间与调度类

#### A1. 业务日界线（BusinessDayBoundary）
- **来源**：`lib/time-utils.js:15` —— 凌晨 4 点前归为前一天，用于日记/记忆的"今天"切片。
- **通用性**：高。任何按"业务日"统计/分组的应用（日报、排班、日志归档）都需要，且语义可配置。
- **Java 实现要点**：`ZonedDateTime` + 偏移量，纯函数，零状态。

#### A2. cron / at / every 解析与 next-run 计算
- **来源**：`lib/desk/cron-store.js:225` —— 三种触发方式的"下次运行时间"纯时间算法。
- **通用性**：高。任何需要定时任务的应用。flora-root 目前无任何 cron 解析能力。
- **Java 实现要点**：cron 表达式（5/6 段）解析器 + `nextRunAt(now)` 纯计算；无依赖可手写，类似 `cron-utils` 的子集。

#### A3. 到期任务轮询调度器（含重试语义）
- **来源**：`lib/desk/cron-scheduler.js:22` —— 每分钟扫 `nextRunAt` 到期任务，执行失败时 **skipped 不推进时间**（下次继续重试）。
- **通用性**：高。后台任务调度是通用结构；"失败不推进 next-run"的重试语义是价值点，与 `concurrent.retry` 互补（retry 管单次调用内的重试，调度器管跨轮的到期语义）。
- **Java 实现要点**：`ScheduledExecutorService` 或自研最小轮询循环；任务注册表 + 到期判定 + 失败标记。

#### A4. 目录指纹心跳巡检（变更监控）
- **来源**：`lib/desk/heartbeat.js:211` —— 周期扫描目录，MD5 短指纹对比注册表检测变化；**执行后重扫描存指纹**，避免任务自身改动触发重复执行（防自激振荡）。
- **通用性**：中高。文件同步、构建监听、配置热加载等都要"周期检测目录变化"。
- **Java 实现要点**：目录快照 + 短指纹（可复用 `entropy.HashUtil`）+ 注册表 diff + 自振荡抑制标记。

### B. 记忆 / 知识存储类

#### B1. 标签化事实存储与检索（TaggedFactStore）
- **来源**：`lib/memory/fact-store.js:20` —— 事实（fact + tags + time）入库；标签精确匹配用 `GROUP BY + COUNT(DISTINCT)` 按命中数降序；FTS5 触发器同步全文索引。
- **通用性**：高。标签化知识条目 + 按标签命中数排序 + 全文检索，是所有知识型应用（笔记、FAQ、文档库）的通用结构。
- **Java 实现要点**：内存 `Map<tag, Set<id>>` 倒排 + 命中数聚合排序；持久化可插拔（文件/DB）。**倒排索引 + 多标签交集排序**是核心算法，与 DB 无关。

#### B2. 分层记忆压缩（滑动窗口 + 渐进式降采样）
- **来源**：`lib/memory/compile.js:34` —— today/week/longterm 四层逐级压缩，近期清晰、旧忆淡忘；内容指纹缓存跳过无变化编译（`computeFingerprint :250`）。
- **通用性**：中高。时间序列/事件流的分层压缩与摘要，是日志聚合、存档类应用的通用思想；其中**内容指纹跳过无变化**部分纯算法可直接入库。
- **Java 实现要点**：滑动窗口 + 降采样策略接口；指纹缓存可复用 `entropy.HashUtil`。LLM 压缩部分属于应用层，不进通用库，但"指纹 diff → 跳过"是通用机制。

#### B3. 滚动摘要 + snapshot diff 增量
- **来源**：`lib/memory/session-summary.js:222`（覆盖式摘要）+ `getDirtySessions :75`（summary ≠ snapshot 判脏）。
- **通用性**：中。事件流的增量聚合与变更追踪（只处理变化部分）——日志增量备份、同步器通用。
- **Java 实现要点**：状态对象 + 快照 + diff 判定，通用 diff/合并结构。

### C. 事件 / 路由类

#### C1. 带过滤的事件总线
- **来源**：`hub/event-bus.js:8` —— 按 sessionPath/type 过滤订阅的 pub/sub。
- **通用性**：高。几乎所有事件驱动应用（GUI、服务器、Agent 编排）都需要。flora-root 目前没有。
- **Java 实现要点**：`EventBus` + 订阅者过滤器（按主题/类型谓词）+ 线程投递策略。可参考现有 `cache` 的可观察/监听器设计风格保持一致。

#### C2. 流式增量标签解析器
- **来源**：`core/events.js:45/181`（MoodParser/XingParser）—— 处理分片输入，`trailingPrefixLen` 持有不完整标签前缀，增量输出。
- **通用性**：高。流式协议/标记语言（SSE 分片、流式日志标记、增量渲染）增量解析的价值很高，是"分段喂入、完整时才产出"的通用解析器形态。
- **Java 实现要点**：可基于 `syntax.peg` 扩展出"增量/分片"变体，或独立小状态机；对外暴露 `feed(chunk)` / `drain()`。

### D. 安全 / 权限类

#### D1. 路径权限判定（PathGuard，四级访问控制）
- **来源**：`lib/sandbox/path-guard.js:37` —— realpath 解析符号链接防逃逸 + `_isInside` 前缀判定 + **fail-closed**（默认拒绝）。
- **通用性**：高。文件系统 ACL / 沙盒 / 目录白名单，任何"限制某进程/Agent 只能访问指定目录"的应用都需要。
- **Java 实现要点**：复用 `runtime.virtual.filesys` 的 `SymlinkFSBackend`（符号链接解析）+ `RealFileSystem`（realpath）+ 路径前缀归一化判定 + fail-closed 默认值。**注意路径规范化（`..`、符号链接、Windows 盘符大小写）是核心难点**。

#### D2. PII 正则脱敏
- **来源**：`lib/pii-guard.js:34` —— 手机号、邮箱、身份证等敏感信息正则识别与替换。
- **通用性**：高。任何处理用户数据的应用（日志脱敏、隐私保护）都需要；实现简单、收益高。
- **Java 实现要点**：`Pattern` 集合 + 替换策略（保留前后若干位），配置驱动。

#### D3. SSRF 防护
- **来源**：`lib/tools/web-fetch.js:25` —— DNS 全记录解析 + 私网 IP 范围匹配 + 逐跳重定向校验。
- **通用性**：中高。任何"让程序抓取 URL"的服务（爬虫、Webhook、Agent 网页浏览工具）都该内置，避免内网探测。
- **Java 实现要点**：DNS 解析（`InetAddress.getAllByName` / 系统 resolver）+ 私网 CIDR 匹配（`10/8, 172.16/12, 192.168/16, 127/8, ::1, fc00::/7` 等）+ 重定向逐跳复检。

### E. 存储类

#### E1. append-only 文件消息流 + per-file 互斥锁 + 时间戳游标增量读
- **来源**：`lib/channels/channel-store.js:24/47/207` —— append-only 消息流 + frontmatter + 原子写 + bookmark 游标。
- **通用性**：高。无 DB 的消息流/日志存储，是本地优先应用（IM、笔记、事件日志）的通用结构；**文件锁 + 原子写 + 游标增量读**三个原语可单独复用。
- **Java 实现要点**：`FileChannel`/`FileLock` per-file 互斥 + 原子写（temp + rename）+ 时间戳游标；`runtime.virtual.filesys` 可承载路径层。

#### E2. 有上限活动记录淘汰
- **来源**：`lib/desk/activity-store.js:16`（`_cleanup :71`）—— 环形淘汰最老记录并级联删除关联文件。
- **通用性**：中。有界历史记录（最近 N 条）+ 级联清理，通知/历史列表通用。优先级低于其他项。
- **Java 实现要点**：环形缓冲（复用 `container`/`fast.container` 的队列）+ 淘汰回调。

### F. 状态与进程类

#### F1. 会话状态重放重建（事件溯源）
- **来源**：`lib/tools/todo.js:27` —— 扫描历史 toolResult 重放恢复状态。
- **通用性**：中高。无状态会话的状态恢复（事件溯源模式），任何"可重启恢复"的系统通用。
- **Java 实现要点**：事件日志 + 重放引擎；可复用 E1 的 append-only 流作为事件日志。

#### F2. 子进程管理（超时 / abort / 进程树杀灭）
- **来源**：`lib/sandbox/exec-helper.js:24`（killTree `:82`）。
- **通用性**：中高。任何需要执行外部命令并确保不泄漏进程的应用（构建工具、沙盒执行器）。JDK `Process` 缺少进程树杀灭。
- **Java 实现要点**：`ProcessBuilder` + 超时 + 递归杀子进程（Unix 用 `kill -PID` / 进程组，Windows 用 `taskkill /T`）；`com.flora.os` 已有平台抽象可承接。

#### F3. 配置深度合并 + 原子写
- **来源**：`lib/memory/config-loader.js:364`。
- **通用性**：中。配置系统通用能力。**与 `runtime.config` 的关系**：应优先在现有 Config 上补"深度合并 + 原子写"缺口，而不是新起炉灶。

---

## 四、与已有能力的复用关系

| 新能力 | 可复用底座 | 复用点 |
|---|---|---|
| D1 PathGuard | `runtime.virtual.filesys` | SymlinkFSBackend 符号链接解析、RealFileSystem realpath |
| C2 流式增量解析 | `syntax.peg` | PEG 状态机扩展为增量/分片变体 |
| F3 配置深度合并 | `runtime.config` | 在 Config 上补 merge + 原子写 |
| A4 指纹巡检 | `entropy.HashUtil` | 短指纹计算 |
| B2 指纹缓存 | `entropy.HashUtil` | 内容指纹跳过无变化编译 |
| F2 子进程 | `com.flora.os` | 平台抽象与 Windows 特殊处理 |

---

## 五、优先级与建议实施顺序

| 优先级 | 能力 | 理由 |
|---|---|---|
| **高** | A2 cron/at/every 解析 + next-run | 定时任务是该应用核心，且 flora-root 完全空白，纯算法无依赖 |
| **高** | A3 到期任务轮询调度器 | 与 A2 配套，重试语义通用 |
| **高** | C1 事件总线 | 多模块解耦的基础设施，几乎所有应用复用 |
| **高** | B1 标签化事实存储 | 记忆系统的数据结构核心，倒排 + 命中排序是通用算法 |
| **高** | D1 路径权限判定 | 沙盒安全核心，可复用 VFS 底座 |
| **高** | E1 append-only 文件流 + 文件锁 + 游标 | 无 DB 消息流存储，三个原语各自通用 |
| **高** | C2 流式增量标签解析器 | 流式协议增量解析，通用且价值高 |
| 中 | A1 业务日界线 | 简单，但常被忽略、语义需要早定 |
| 中 | A4 目录指纹心跳 | 变更监控通用，实现成本低 |
| 中 | D2 PII 脱敏 | 实现简单收益高 |
| 中 | D3 SSRF 防护 | 安全基线，任何抓 URL 的应用需要 |
| 中 | B3 滚动摘要 + snapshot diff | 增量聚合通用 |
| 中 | F1 会话重放 | 事件溯源模式 |
| 中 | F2 子进程管理 | 进程树杀灭是 JDK 缺口 |
| 低 | B2 分层记忆压缩 | LLM 依赖强，纯算法部分（指纹跳过）已由 B3/哈希覆盖 |
| 低 | E2 活动记录淘汰 | 简单，可后用 |
| 低 | F3 配置深度合并 | 尽量在现有 Config 上补，不进新包 |

**建议先做一批（高优先级 7 项）**：它们构成"时间调度 + 事件 + 存储 + 安全"四根柱子，既服务 openhanako 复刻，也是其他应用可直接复用的通用基座。实现时按 `com.flora.tag` 标注语义/目的/注意事项（如 `@SuitedFor` 适用场景、`@ConcurrencyNote` 线程语义）。
