# explore20260812-deepseek-harness 插件热插拔评估

## 结论

**支持，而且是完整的运行时热插拔**——这是 deepseek-harness（DSH）的核心能力之一，基于
Koishi 的 **Cordis 插件框架**。

## 三层机制

### 1. 动态包（模型驱动热插拔，最核心）

`packages/extensions/tool-cordis` 提供五个面向模型的工具，操作当前 DSH 进程的实时运行时：

| 工具 | 作用 |
|---|---|
| `cordis_define` | 登记一个包（name/purpose + host 半 code + 可选浏览器半 client），**只登记 + 语法预检，不执行** |
| `cordis_run` | 在 vm 沙箱求值 host 半，把浏览器半投递给打开的页面 |
| `cordis_stop` | 把 host 半 dispose 停稳，撤回浏览器半；定义存续可再 run |
| `cordis_undefine` | 必要时先 stop 再忘掉定义 |
| `cordis_inspect` | 只读检视：服务、存活插件 fiber、已注册工具、本会话动态包、api/events/client 槽 |

- **两阶段生命周期**：`define` 只做登记（无副作用），一切带副作用动作挂在一次 `run` 上。
- **运行中任意 define/run/stop/undefine 组合，无需重启**。

### 2. 机制细节（成熟度体现）

- 内存注册表即唯一真源（`registry.ts`），可跨后续轮次保持活跃；
- 失败回退：host 半失败在浏览器动作前短路；幂等 run；同定义并发只求值一次；版本过期的作答被拒绝；
- 挂起请求有取消路径（调用方 AbortSignal）；无页面连接时浏览器半 run 会挂起直至取消；
- 会话隔离：别的会话登记的定义"读起来不存在"，跨会话不泄漏；运行控制面全局但每动词查归属；
- 浏览器半渲染失败有 `reportRenderFailure` 上报路径（fire-and-forget）。

### 3. 局限与边界

- **不跨重启存续**（进程内存）；不会自动转成正式插件；不写任何插件文件/cordis.yml/配置；
- 带浏览器半的包在 headless / ACP 部署会挂起（无超时），无人值守自动化用不了带 UI 的包；
- vm 沙箱是全局隔离**不是安全边界**（README 明确"应当像对待 bash 访问一样对待"）；
- 动态工具 schema 变化从第一个变化 token 起使 KV cache 前缀复用失效。

## 信任立场

host 半收到不含框架内部机制的 façade，获准服务（ctx.fs/web/bash/timer）仍会触达存活运行时，
逃逸成为可能——因此对动态包应视为代码执行权限，而非受信插件。

## 可借鉴点（对 flora 生态）

- **两阶段生命周期**（define 登记 / run 执行）是"模型驱动加载插件"的干净范式，天然适合
  权限门控与失败回退；
- **会话级隔离 + 归属检查**可复用于多租户/多会话 agent 的插件沙箱；
- **host 半（服务）/ 浏览器半（UI）分离 + 槽目录**的模式可复用于 flora 的 CLI/TUI/GUI/IM
  多前端插件架构；
- 明确"动态包 ≠ 正式插件"（不跨重启、需经正式开发流程固化）避免热插拔机制被误当持久化方案。
