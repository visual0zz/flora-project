# explore20260812-argus 架构调研

## 调研对象与假设

- 目标：`lileding/argus`（https://github.com/lileding/argus）——Rust 编写的个人 AI 助理 agent，
  多模型编排、结构化数据、Feishu 集成，2026-04 创建，MIT，附 DESIGN 文档。
  定位："One assistant, one memory, one timeline. Not a chatbot. A personal AI assistant agent."
- 说明：同名项目较多（还有 kungfusaini/argus 的 Node 个人助理、Sec-Link/Argus 的安全 SOC 平台等），
  本文按与 crush/openclaw/codebuddy 可比性选择前者；若实际指其他 argus，结论需重新调研。

## argus 核心架构

### 分层与依赖方向

- **三层单向依赖**：`Gateway → Agent → Upstream`，禁止反向引用。
- Agent 不引用 Gateway 任何类型：入站 `Message` 携带 `mpsc::Sender<Notification>` 端口克隆
  （从 Gateway 的单向出站 MPSC 复制），Agent 回话无需知道对端是哪个 IM。
- 工具也通过端口回推：工具层与 Agent 之间同样以消息/端口交互，不直接耦合类型。

### 进程模型：单进程五对等服务

五个对等组件用 `tokio::join!` 并发（刻意不用 `Arc`、不用 `tokio::spawn`）：

| 服务 | 职责 |
|---|---|
| Gateway | IM 适配（Feishu：WS 入站、媒体、卡片渲染） |
| Agent | 分发器 + 每 channel 的 Processor |
| Embedder | 后台嵌入、长回复摘要、文档摄取 |
| Recovery | 重启后重放未回复消息（每 5 分钟兜底） |
| Scheduler | 持久 Cron，每 60s 触发 |

### 生成模型：两阶段 Agent

- **Orchestrator**：只调工具，文本输出被忽略，循环直到 `finish_task`；
- **Synthesizer**：无工具、无历史，只接收 Orchestrator 产出的 `Materials` 块，合成最终答复；
- 两阶段可各用**不同模型/提供商**；
- 异步任务（`create_task` 工具）在 `FuturesUnordered` 池中跑后台 orchestrator→synthesizer 循环，
  与同步消息并发。

### 事件 / 消息模型

- 入站 `Message`（Gateway→Agent）：携带 `sink` + `ready: oneshot<Payload>`；
- 出站 `Notification`（Agent→Gateway）：携带 `events: Receiver<Event>`；
- `Event` 枚举 = `ToolStatus | Composing | Reply`；
- `Channel`（i64）= 租户单位；`Sink`（如 `feishu:p2p:ou_xxx`）= IM 端点；多 sink 映射到一 channel；
- 消息按 sink 路由，任务按 channel_id 路由。

### 记忆

- 滑动窗口（channel 级近期轮次）；
- **pgvector 语义召回**：嵌入当前消息，对 messages/notifications 做 cosine 搜索，阈值 0.50，6KB 预算；
- pinned memories（memories 表）+ 长回复摘要（Embedder 生成）；
- **LLM 只见精选上下文，不见原始历史**。

### 工具与护栏

- 工具集：finish_task / current_time / search / fetch / read_file / write_file / cli /
  db（7 个结构化动词，无裸 SQL）/ remember / forget / search_docs / list_docs / search_history /
  activate_skill / create_task / create_cron 等；
- **弱模型加固**：每工具硬预算 + 累计 strike-out + 纯文本提前中止 + 最大迭代护栏。

### 模型抽象与可观测性

- named upstreams：openai / openai-response / anthropic（extended thinking）/ gemini（OpenAI 兼容端），
  全部基于 reqwest，无 SDK；per-call `ChatOptions { thinking_budget }`；
- 每个角色（orchestrator/synthesizer/transcription/embed/summarize）独立选上游；
- 全请求 tracing（traces + tool_calls 表）；崩溃 Recovery 服务；持久 Cron。

## 与 crush / openclaw / codebuddy 的对比

| 维度 | argus | crush | openclaw | codebuddy |
|---|---|---|---|---|
| 定位 | IM 聊天助理（Feishu） | 编码 CLI agent | IM 家庭助理 | 编码 IDE agent |
| 生成模型 | **两阶段**：Orchestrator 纯工具 → Synthesizer 纯措辞 | 单循环（想-调-答一体） | 单循环 | 单循环 |
| 通道解耦 | 端口回推，Agent 完全不知 Gateway | 无通道概念（stdin/工具调用） | channel 插件，agent 感知通道 | IDE 集成 |
| 记忆 | 语义召回（pgvector）+ 预算裁剪，显式设计 | memory 文件（指令级） | 长上下文为主 | 项目/仓库上下文 |
| 工具护栏 | 硬预算 / strike-out（防弱模型） | 权限系统 + hooks 门控 | 默认信任（permissive） | 沙箱代码执行 |
| 进程形态 | 单进程五服务 join!（无 Arc/spawn） | 单 CLI 进程 | 常驻服务 + 多通道 | IDE 进程 |
| 可观测/恢复 | tracing 表 + Recovery 重放 | 日志级别 | 弱 | IDE 侧诊断 |
| 面向任务 | 对话/聊天（可含异步任务与 Cron） | 代码文件编辑 | 家庭自动化 | 编程任务 |
| 关键原语 | 事件流 + 两阶段 + 语义记忆 | 工具调用 + 权限/hooks | 通道 + 人格 + 信任 | IDE + 代码执行 |

## 核心差异总结

1. **两阶段生成**是 argus 最独特之处：把"思考与工具执行"和"最终措辞"拆成两个可独立选模型的
   阶段（Synthesizer 无工具、无历史、只拿 Materials）。crush/openclaw/codebuddy 均为单循环。
2. **端口级解耦**：Agent 通过 Message 携带的 Sender 回推，对 Gateway 零类型依赖；
   比 openclaw 的 channel 插件（agent 可见通道）更严格。
3. **显式语义记忆**：pgvector 召回 + 预算裁剪，LLM 只看精选上下文；crush 用文件指令记忆，
   openclaw/codebuddy 偏长上下文。
4. **弱模型护栏**：工具硬预算/strike-out 是针对本地小模型的设计；crush 用权限 + hooks，
   openclaw 默认信任。
5. **可观测与恢复**：tracing + Recovery 服务（重启重放）是 argus 独有组件。
6. 场景本质差异：argus 为"聊"（Feishu 会话 + 异步任务 + Cron）设计，crush/codebuddy 为
   "写代码"设计（编辑/执行/上下文），openclaw 为"家庭自动化"设计（通道 + 信任）。

## 可借鉴点（对 flora 生态）

- 两阶段生成（工具循环与措辞分离）可用于需要"先想后答"的 agent 场景；
- 端口回推解耦模式可复用于多前端（CLI/TUI/GUI/IM）的 agent 架构；
- 语义记忆 + 预算裁剪适合有持久记忆需求的个人应用；
- 弱模型护栏（预算/strike-out）对 flora 本地模型路线有直接参考价值。
