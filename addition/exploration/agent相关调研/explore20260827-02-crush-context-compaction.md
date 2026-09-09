# explore20260812-crush-context-compaction

## 背景

在 flora-sanctum 系列重构会话中，Crush（charmbracelet/crush，opencode 的 fork）触发了多次上下文压缩（context compaction）。
压缩后表现出两个明显问题：

1. **重做已完成的工作**：压缩前已 commit/push 的工作，压缩后 agent 又尝试重做。
2. **完全跑偏**：压缩前 agent 已自行纠正的"幻觉任务"（如清理 `action/tmp/`），
   在压缩摘要里被固化成"真实待办"，压缩后 agent 又去执行，形成幻觉闭环。

本文先基于现象推测压缩流程，再去 opencode 源码验证，并迭代完善，最后给经验总结。

---

## 一、环境事实（已确认）

- Crush 经 npm 安装（`@charmland/crush`），是 opencode 的 fork（Go 编译，本地无源码）。
- 配置：`C:/Users/shutie.zhao/AppData/Local/crush/crush.json`（large=hy3, small=deepseek-v4-flash）。
- 会话状态：`C:/Users/shutie.zhao/.crush/`（crush.db, init, logs/crush.log）。
- `crush.log`（287KB）中搜 "compac" 无结果 => 压缩动作不落在这份内部日志，是 session 内 context 管理。
- `crush_logs` 工具只记录 provider/LSP/工具错误，不记录会话历史。

## 二、源码验证（opencode-ai/opencode, main 分支）

### 2.1 自动触发条件 — `internal/tui/tui.go`
压缩**只在一次正常响应完成后**才被触发，且看的是"下一次响应完成"时的累计 token：

```go
} else if payload.Done && payload.Type == agent.AgentEventTypeResponse && a.selectedSession.ID != "" {
    model := a.app.CoderAgent.Model()
    contextWindow := model.ContextWindow
    tokens := a.selectedSession.CompletionTokens + a.selectedSession.PromptTokens
    if (tokens >= int64(float64(contextWindow)*0.95)) && config.Get().AutoCompact {
        return a, util.CmdHandler(startCompactSessionMsg{})
    }
}
```

- 阈值：`PromptTokens + CompletionTokens >= 0.95 * ContextWindow`，且 `autoCompact` 开启（默认 `true`）。
- 关键点：**触发是滞后的**——超预算的那一轮本身不会被压缩，要等它成功返回、下一轮响应完成时才会触发。
  所以"压扁"的是已经溢出窗口的历史，而非当前正在生成的内容。
- 也可手动 `/compact` 命令触发（`RegisterCommand` 中 `"compact"`）。

### 2.2 摘要生成 — `internal/llm/agent/agent.go` 的 `Summarize()`
- 取**全部历史消息**（`a.messages.List`，包含 ToolCall / ToolResult 等所有 part）发给独立的 `summarizeProvider`。
- 系统提示（`internal/llm/prompt/summarizer.go`）：
  ```
  You are a helpful AI assistant tasked with summarizing conversations.
  ... Focus on ...: What was done / What is currently being worked on /
  Which files are being modified / What needs to be done next
  ```
- 追加的用户提示（inline）：
  ```
  Provide a detailed but concise summary of our conversation above. Focus on ...
  what we did, what we're doing, which files we're working on, and what we're going to do next.
  ```
- 摘要存为一条 `Assistant` 消息，并写入 `session.SummaryMessageID`。
- **没有任何结构化状态要求**：不要求 commit hash、git status、测试结果、token/进度锚点。纯自由文本摘要。

### 2.3 压缩后历史如何拼接 — `agent.go` 的 `processGeneration()`
这是理解"跑偏"的核心：

```go
if session.SummaryMessageID != "" {
    summaryMsgInex := -1
    for i, msg := range msgs {
        if msg.ID == session.SummaryMessageID { summaryMsgInex = i; break }
    }
    if summaryMsgInex != -1 {
        msgs = msgs[summaryMsgInex:]
        msgs[0].Role = message.User   // 摘要被重新标为 User 消息
    }
}
```

- 压缩后，原始 tool_call/tool_result **不再喂给 coder**，仅摘要文本留在活动窗口。
- **摘要被重新标记为 `User` 角色**置于历史头部。
  - 影响：模型可能把摘要内容当成"用户陈述的事实/指令"，而非"自己的记忆"。
    这会放大"把摘要里列的 next steps 当作待执行命令"的倾向（见 H3/H4）。
- **没有任何专门的 resume 提示注入**。摘要只是变成对话头部，下一轮照常 `processGeneration`。
- 每次 `processGeneration` 都通过 provider 重新拼接 **coder 系统提示**（`CoderPrompt`），
  其中包含 `getEnvironmentInfo()`——会重新跑 `ls .` 并报告 cwd / 是否 git 仓库 / 平台 / 日期。

### 2.4 环境信息再注入 — `internal/llm/prompt/coder.go`
```go
func getEnvironmentInfo() string {
    cwd := config.WorkingDirectory()
    isGit := isGitRepo(cwd)        // 只检查 .git 目录是否存在
    platform := runtime.GOOS
    date := time.Now().Format("1/2/2006")
    ls := tools.NewLsTool()
    r, _ := ls.Run(... `{"path":"."}`)   // 只列目录，不跑 git
    return fmt.Sprintf(`... Working directory / Is git repo / Platform / Today's date / <project> ls 输出 </project>`)
}
```
- 每轮重新注入的 env 块**只做 `ls`，不跑 `git status` / `git log`**。
- 因此压缩后 agent 能看到"cwd、是 git 仓库、文件清单、日期"，但**看不到 HEAD 位置、已提交内容、工作树差异**。
- coder 提示里只说"可用 git log/git blame 查历史"，是否主动去查完全取决于 agent 自己。

---

## 三、假设验证结果

| 假设 | 结论 | 证据 |
|---|---|---|
| H1 压缩丢弃原始 tool_call/result，只留叙述摘要 | **确认** | `processGeneration` 截断到 summary 后；原始 tool part 不回喂 coder |
| H2 摘要丢失"已完成"的判定依据，只留结论 | **确认** | `SummarizerPrompt` 不要求结构化状态；env 块不跑 git |
| H3 摘要 next steps 是开放式、压缩后模型默认当成待做 | **强化确认** | 摘要被 `Role = message.User` 重标为用户消息，更易被当指令 |
| H4 摘要固化压缩前的幻觉并导致跑偏 | **确认（机制）** | 摘要是自由文本，压缩前模型幻觉会被写进去；无校验锚 |
| H5 缺少"硬状态锚"机制 | **确认** | env 块只 `ls`；无 git status/log 锚；无 commit hash |

补充发现（源码新增）：
- **触发滞后**：压缩在"溢出后的下一轮响应完成"才触发，不是溢出当轮。
- **无 resume 提示**：压缩后无专门系统/用户提示，摘要仅成对话头。
- **摘要角色被改为 User**：这是 opencode 特有的设计，会放大"把 next steps 当命令"。

---

## 四、根因小结

压缩器做了"故事复述"而非"状态快照"：
1. 用纯自由文本摘要替换了含证据（工具输出、git 结果）的原始历史；
2. 摘要模板只问"做了什么/在做什么/下一步"，**不要求任何可机器验证的锚**；
3. 压缩后每轮只重新注入 `ls` 级别的环境信息，**不重新建立 git 真相**（HEAD、已提交、工作树干净度）；
4. 摘要被标为 `User` 消息，使模型倾向于把"下一步"当成用户下达的待办；
5. 压缩前模型的幻觉会被原样写进摘要并固化，形成"幻觉→摘要→重做幻觉"闭环。

这正是"重做已完成工作"和"完全跑偏"的机理。

---

## 五、经验总结（给用户 / 给 AI 行为规范）

### 5.1 对用户（如何配置/操作以降低风险）
- **关键任务前手动 `/compact` 不可取**：反而应在长任务的"干净检查点"（已 commit、已测试）之后让压缩自然发生，但压缩前最好先把真相写进对话。
- 可在 `crush.json` / `.opencode.json` 设 `"autoCompact": false`，改为**手动在检查点触发**，避免 95% 阈值在"脏状态"时自动压缩。
- 压缩后第一件事应让 agent 跑 `git status` + `git log -1` 重建真相，而不是直接信任摘要。

### 5.2 给 AI 行为规范（AGENTS.md / OpenCode.md 应加的规则）
- **压缩前自写"硬状态锚"**：在预计会触发压缩的长任务中，定期把可验证状态写入对话，
  例如：`最新 commit = <hash>`、`git status 干净`、`测试命令 <cmd> 已通过`。
  这样摘要若引用这些锚，压缩后 agent 能凭锚自检，而非凭叙述重做。
- **把"已完成"与"待做"显式分开**：在摘要-friendly 的位置明确写
  "以下已完成（勿重做）：..."和"以下待做：..."，对抗摘要被当 User 指令的倾向。
- **压缩后立即重建真相**：压缩后的第一轮，先 `git status` / `git log` / 跑测试，
  用真实输出覆盖摘要里的叙述，再继续。
- **不要依赖摘要里的 next steps 作为命令**：把摘要视为"记忆"而非"指令"；
  对摘要中出现的任务先用工具验证其真实性，再执行。

### 5.3 给 Crush/opencode 上游的改进建议（若可提 PR）
- 摘要模板应要求结构化尾部，例如强制输出 `## Verified State` 块（commit hash、git status、test result）。
- `getEnvironmentInfo()` 可改为在压缩会话中附带 `git status --short` 与最近 commit，提供真实锚。
- 摘要消息的角色不必强制改为 `User`（保留 `Assistant` 可减少被当指令的可能）。

---

## 六、待办
- [x] 写初始推测文档
- [x] 定位并阅读 opencode compaction 源码（agent.go / tui.go / summarizer.go / coder.go）
- [x] 逐条验证 H1–H5，补充触发滞后 / 无 resume 提示 / 摘要改 User 角色
- [x] 给出经验总结与可落地规则
