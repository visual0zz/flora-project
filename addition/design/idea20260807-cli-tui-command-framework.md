# CLI / TUI 通用命令框架设计方案

日期：2026-08-07
状态：方案稿（待评审）

## 1. 背景与目标

项目内 `flora-osmetes`、`flora-ramet`、`flora-tangle` 的命令行入口全部是手写位置参数解析 + `System.out` 打印，重复度高、无统一帮助、无法复用。同时 AI Agent 需要把工具以结构化方式暴露，未来还可能有交互式工具（类 tmux、类 vim）。本项目当前没有任何 CLI 参数解析框架、ANSI 渲染层或 TUI 代码。

本框架的目标：

- 一套命令定义，同时服务于 **CLI（一次性执行）**、**TUI（常驻交互）**、**AI Agent（结构化调用）** 三种前端；
- 命令类**自描述**：名称、参数、帮助都聚合在命令类内部，help 由聚合层统一生成，而非散落各处的字符串；
- 允许"一处定义共享"，也允许**按前端特化**（某个命令只给 TUI 定义交互视图、只给 Agent 定义返回 schema）；
- **零第三方依赖**；
- 心智模型尽量小：三个概念讲清全部设计。

## 2. 心智模型

> 一个命令 = 一个类。命令类自己说出"我叫什么、要什么参数、做什么、帮助是什么"。
> 框架负责"把它接到 CLI、TUI、Agent 上"。

三个核心概念：

1. **Command（命令）**——自描述的执行单元，一个类一个命令。
2. **CommandRegistry（注册表）**——命令的集合，负责分派与 help 聚合。
3. **Frontend（前端）**——把命令接入某种交互环境：CLI、TUI、Agent。

书写一个新命令的默认路径只有四步（回答四个问题）：

```
叫什么（name） → 做什么（description） → 收什么参数（args） → 收到后干嘛（execute）
```

四个问题全部写在同一个类里。做到这四步，CLI 与 Agent 前端开箱即用；TUI 前端提供通用的参数表单渲染。只有需要**专属交互**（vim 的按键模式、tmux 的窗格操作）时，命令才额外实现前端特化接口——这是可选的增量，不是默认负担。

## 3. 模块与包结构

推荐放入 `flora-root`（零依赖工具库，所有工具模块已依赖它），顶层包 `com.flora.shell`。
备选：独立 `flora-shell` 模块（若担心 flora-root 继续膨胀；代价是其他模块需新增依赖）。

```
com.flora.shell
├── Command                  # 命令接口（声明 + 执行 + 特化入口）
├── CommandRegistry          # 注册表：注册、查找、分派、聚合 help
├── Invocation               # 一次命令调用上下文
├── Output                   # 输出通道抽象
├── CommandResult            # 执行结果（退出码 / 结构化数据）
├── spec/
│   ├── ArgSpec              # 参数/选项声明（声明式，非解析代码）
│   ├── ParsedArgs           # 解析结果
│   └── ArgParser            # 零依赖解析器（cli 串 / 列表 / JSON 输入）
├── help/
│   ├── HelpProvider         # 命令向 help 聚合层提供数据
│   └── HelpRenderer         # 渲染成文本树 / 分屏 / Agent 工具描述
└── frontend/
    ├── Frontend             # 前端接口（CLI/TUI/Agent 都实现它）
    ├── CliFrontend          # 一次性：argv → 执行 → 打印 → 退出码
    ├── AgentFrontend        # 结构化：命令 ↔ 工具 schema ↔ JSON 结果
    ├── TuiFrontend          # 常驻：事件循环 + 渲染循环
    └── tui/                 # TUI 原语（零依赖 ANSI 实现）
        ├── RawTerminal      # 原始模式开关
        ├── KeyEvent         # 按键事件
        ├── ScreenBuffer     # 离屏缓冲 + ANSI 差分刷新
        └── Layout           # 窗格/布局（split、状态栏、命令面板）
```

## 4. Command 定义模型

接口示意（设计契约，非实现）：

```
interface Command {
    // —— 声明层：自描述，供 help / 参数解析 / Agent schema 共用 ——
    String name();                      // 命令名（子命令用 '.' 分隔，如 "buffer.write"）
    String description();               // 一句话说明
    List<ArgSpec> args();               // 参数声明，默认空
    String usage();                     // 可选：手写一行用法覆盖自动生成的用法

    // —— 执行层：一次调用 ——
    CommandResult execute(Invocation ctx) throws Exception;
}
```

- `name()` 支持点分路径，天然表达**子命令树**（`session.new`、`buffer.write`），注册表按前缀聚合为命令树。
- `args()` 是**声明**不是代码：解析、help 生成、Agent 的 JSON schema 都从这同一份声明推导，保证"一处定义、处处一致"。
- `execute()` 不直接碰 `System.out`，而是通过 `Invocation` 里的 `Output` 写输出，因此同一份实现可同时跑在 CLI 打印与 TUI 面板上。

### 前端特化（可选增量）

命令类可以额外实现以下接口，按需提供专属行为：

```
interface CliView {      // CLI 专属：argv 级前置校验 / 定制错误输出，默认由框架提供
    default String beforeExecute(List<String> rawArgs) { ... }  // 返回 null 或错误
}
interface TuiView {      // TUI 专属：按键绑定、会话内视图
    default void bindKeys(KeyMap km) { }
    default View view(Session s) { return null; }   // null = 用通用表单
}
interface AgentView {    // Agent 专属：定制工具描述 / 返回值 schema，默认由声明自动生成
    default ToolSchema toolSchema() { ... }
}
```

心智模型规则：**"不实现任何特化接口"= 双端/三端通用；"实现某个特化接口"= 只对该前端生效，其他前端仍走默认。** 特化只针对行为，不复制声明——声明永远只有 `Command` 接口那一份。

## 5. 参数声明与解析

`ArgSpec` 声明式描述参数，覆盖 CLI 与 Agent 两种输入形态：

- **选项**：`--name` / `-n`（可带值、可重复、可 bool 开关）
- **位置参数**：`<src>`（可选/必选/变长）
- **值约束**：类型（int/str/enum/list）、默认值、合法枚举、数值范围
- **组合规则**：互斥、必选其一（由解析器校验）

解析器只有一个入口，`ParsedArgs parse(List<String> argv)`；AI Agent 输入（JSON 对象）先归一化成同一种 `Map<String,Object>` 再走同一校验，从而 **CLI 的 `--port 8080` 与 Agent 的 `{"port":8080}` 落到同一套校验逻辑**。

解析失败时返回结构化的错误（缺哪个参数、哪个值非法、期望什么），由各前端按自己的方式呈现：CLI 打印到 stderr 并置非零退出码，TUI 画到命令面板，Agent 回传错误对象。

## 6. Help 聚合到命令类内部

设计原则：**命令类是 help 的唯一事实来源**。`Command` 的声明层（name/description/args/usage）就是 help 数据，不需要单独维护一份帮助字符串。

- 单命令 help：由命令类声明 + 示例段自动渲染。
- 全局 help：`CommandRegistry` 遍历所有命令，按点分名构建命令树，`HelpRenderer` 渲染：
  - **CLI**：`cmd --help` → 文本树打印；
  - **TUI**：帮助页以分屏/滚动视图展示；
  - **Agent**：`--help` 对应的不是文本，而是把整棵命令树转成工具列表描述，随 `toolSchema` 一起交给模型。
- 聚合顺序与层级由注册表保证，命令之间无感知，新增命令自动出现在全局 help 中，无需改动聚合代码。

## 7. 前端抽象

`Frontend` 接口（设计契约）：

```
interface Frontend {
    String id();                    // "cli" | "tui" | "agent"
    void run(CommandRegistry registry, List<String> argv);  // 入口
}
```

三个前端共享注册表与命令定义：

| 前端 | 生命周期 | 输入 | 输出 | 退出 |
|---|---|---|---|---|
| CliFrontend | 一次调用即结束 | argv / 命令串 | stdout/stderr 文本 | 退出码（0/1/2） |
| TuiFrontend | 常驻，事件循环 | 键盘 / 命令输入框 | 屏幕（ANSI 缓冲） | 用户退出 |
| AgentFrontend | 按会话多次调用 | 结构化调用（JSON） | 结构化结果（JSON） | 每次调用返回结果对象 |

**Invocation 暴露当前前端**：命令可通过 `ctx.frontend()` 得知自己在哪种环境，需要时给不同环境不同行为（如 TUI 下返回后驻留、CLI 下返回后退出）。`Output` 抽象屏蔽"打印到终端"与"画到面板"的差异。

## 8. TUI 原语（零依赖 ANSI）

TUI 前端不依赖任何第三方库，直接用 ANSI 转义序列实现四件套：

- **RawTerminal**：Unix 用 `ProcessBuilder` 调 `stty raw -echo`（进入）与 `stty sane`（恢复），进程退出钩子保证恢复；**Windows 原生 raw mode 无公开 Java API，作为已知限制**，标注为有限支持（见 §11）。
- **KeyEvent**：从原始字节流解析按键（普通键、方向键、Ctrl 组合、Fn），跨终端映射到统一键名，供 `KeyMap` 绑定。
- **ScreenBuffer**：离屏字符缓冲 + 全量 diff，每次渲染只输出变化区域，避免整屏闪烁。
- **Layout**：窗格树（split 上下/左右）、状态栏、命令输入面板。类 tmux 的窗格操作、类 vim 的编辑区/命令行区分工都由这一层承载。

现有 `com.flora.os.windows.ShellColorConst`（ANSI 颜色常量，当前未导出未使用）可迁移进 `tui/` 作为颜色基元，顺带解决其"定义即闲置"的状态。

## 9. 场景映射

| 场景 | 前端 | 典型命令 | 特化点 |
|---|---|---|---|
| AI Agent | AgentFrontend | `osmetes.check`、`ramet.gen` | 自动生成工具 schema 与 JSON 结果；需要机器可读返回时实现 `AgentView` |
| 普通 shell 指令 | CliFrontend | 现有三个模块的入口收编 | 默认即用 |
| 类 tmux | TuiFrontend | `session.new`、`pane.split`、`pane.kill` | `TuiView.bindKeys` 绑窗格快捷键，`Layout` 管窗格树 |
| 类 vim | TuiFrontend | `buffer.write`、`search.next` | 命令执行中持有"会话状态"（当前文件/光标），模式切换由 `bindKeys` 表达 |

四种场景**共享**：命令声明、参数解析、help 聚合、注册表。**差异**：仅在前端层与可选的命令特化层。

## 10. 扩展机制

新增一类使用方式时，只需要：

1. 实现一个新 `Frontend`（新环境）；
2. 若个别命令需要该环境专属行为，定义一个新的特化接口（如未来加 `WebView`），命令按需实现。

新增命令时，只需要：写一个 `Command` 类 + 一行注册。`CommandRegistry` 提供注册、按名查找、解析调用、生成 help 树四个能力，命令间完全解耦。

## 11. 零依赖策略与已知难点

- **ANSI 渲染 / 屏幕缓冲 / 布局**：纯 JDK 可完成，无难点。
- **参数解析**：手写状态机即可，本项目入口现状已证明规模可控。
- **原始模式**（TUI 专属）：Unix 借 `stty` 子进程实现零依赖；**Windows 无公开 Java API**。方案：Windows 下通过 `powershell` 调 `SetConsoleMode`，若不可行则明确降级为"TUI 在 Windows 仅支持有限交互、CLI 不受影响"。这是如实声明的工程权衡，不掩盖限制。

## 12. 与现有代码的关系（收编路径）

- `OsmetesCli`、`Ramet`、`Tangle` 的入口改为"命令类 + CliFrontend"，手写参数循环替换为 `ArgSpec` 声明，帮助文本由声明生成，行为不变、代码量下降。
- `ShellColorConst` 迁入 `tui/` 复用。
- `flora-root/module-info.java` 需新增 `exports com.flora.shell` 及子包导出（若采用 §3 推荐方案）。

## 13. 决策点与开放问题

1. **放置位置**：flora-root（推荐）还是独立 flora-shell 模块——需用户确认。
2. **命令命名**：点分路径（推荐）与嵌套 `CommandGroup` 对象两种表达子命令树的方式，前者更贴合"心智模型简单"，后者层级能力更强。
3. **Windows TUI 支持程度**：有限支持 vs 明确不支持（仅 CLI），需用户拍板。
4. **参数校验错误模型**：统一"结构化的参数错误"是否也用于 CLI 的 stderr 展示格式，待定。
5. **多前端共享 Session 的输入归一化**：键盘的 `KeyEvent` 与远程消息（如微信文本）需统一成同一套 `InputEvent` 才能进同一条执行流，归一化层放在 Frontend 内还是 `Invocation` 之前，待定。
6. **多前端执行串行化**：多个前端同时发命令会抢 `Session` 状态，是否由 `Session` 内置单执行锁（或单线程事件循环）强制排队，待定。

## 14. 多前端共享 Session（微信 + TUI 融合场景）

**需求**：一个 Agent TUI 连接微信，微信发来的消息与本地键盘输入"汇总到同一个流"一起执行、一起显示——类比一个 tmux session 被两个 ssh 同时访问。

**结论**：框架原生支持，且**命令声明层零改动**。本质是把微信当成一个新的 `Frontend`，与本地 `TuiFrontend` 同时 attach 到**同一个 `Session`**，并把命令结果写进**同一个可扇出的输出流**。

### 14.1 拓扑对应（tmux 多 client 模型）

| tmux | 框架 | 说明 |
|------|------|------|
| session | `Session` | 常驻状态（光标、当前文件、窗格树）——**唯一** |
| client (ssh) | `Frontend` | 输入源 + 输出目的地——**可多个** |
| 共享输出缓冲 | `Output` | 当前为单一抽象，需升级为可订阅（§14.3） |

两个前端共用 `Session`，即自动共享执行上下文与显示内容——"汇总成一个流"的核心正在于此。

### 14.2 融入方式（三处都在框架边界，不碰命令）

1. **微信连接 = 一个新的 `Frontend`**
   实现 `Frontend` 接口（`id()` 返回如 `"wechat"`），其 `run` 内部不是读键盘，而是起一个长连接读消息；每条消息按同一条解析路径变成 `Invocation`，调 `registry.dispatch(...)`，与键盘走的是**同一个** `dispatch`。命令代码对此完全无感知。

   ```
   // 设计契约示意
   class WeChatFrontend implements Frontend {
       String id() { return "wechat"; }
       void run(CommandRegistry registry, List<String> argv) {
           connect();                       // 建立微信长连接
           while (alive) {
               String msg = readMessage();  // 阻塞读微信消息
               Invocation ctx = registry.parse(msg.lines());  // 同一解析路径
               CommandResult r = registry.dispatch(ctx);      // 同一执行路径
               ctx.out().println(r.message());                // 同一输出流
           }
       }
   }
   ```

2. **`Output` 升级为可扇出**
   当前设计里 `Invocation.out()` 返回单一 `Output`。要"两边都能看到"，把 `Output` 退化成一个扇出器 `OutputMultiplexer`，下面挂多个 `OutputSink`：本地 `ScreenSink`（写 TUI 屏）与 `WeChatSink`（回写微信连接）。命令只管 `out().println(...)`，各 sink 都收到，自然"一起显示"。

   ```
   interface OutputSink { void emit(String text); void emitError(String text); }
   interface Output {
       void print(String s);  void println(String s);  void error(String s);
   }
   // OutputMultiplexer 实现 Output，把每次调用 fan-out 到所有已挂载的 OutputSink
   class OutputMultiplexer implements Output {
       void attach(OutputSink s);   // TuiFrontend 挂 ScreenSink，WeChatFrontend 挂 WeChatSink
       // print/println/error 内部遍历所有 sink 广播
   }
   ```

3. **`Session` 是执行流的汇合点**
   `Session` 已持有常驻状态，命令执行时从 `Invocation.session()` 取状态、写回状态。两个前端共用它，命令结果即共享上下文。这也解释了为什么"命令层零改动"：命令仍只声明 `args/usage/execute`，不感知输入来自键盘还是微信。

### 14.3 需要补强的两块框架基础设施

当前方案主体按"单前端"描述，落地多前端融合还差两块，但都属于扩展路径的题中应有之义，**不修改命令**：

- **输入事件归一化（对应 §13 开放问题 5）**：键盘产生 `KeyEvent`，微信消息是字符串。要让它们进同一条流，需一层把微信消息也包成统一的 `InputEvent`（或复用 `ParsedArgs` 构造路径），否则两个前端各自解析会分叉。建议归一化层放在 `Frontend` 与 `Invocation` 之间，命令永远只看到统一的 `Invocation`。
- **执行串行化（对应 §13 开放问题 6）**：两个前端若同时发命令会抢 `Session` 状态。`Session` 应内置一把执行锁（或单线程事件循环），命令排队执行——这正是 tmux 多 client 共享 session 时的内在约束。锁的粒度与超时策略是待定实现细节。

### 14.4 命令层零改动原则重申

这是关键收益：你写的 `Command`（`name()/args()/usage()/execute()`）完全不知道输入来自键盘还是微信。若某些指令需限制来源（如只允许 TUI 触发、不允许微信触发），也只在特化接口（如新增 `SourceRestricted` 或复用 `TuiView`/`AgentView` 的语义）声明，仍是"一处定义、按需特化"，不污染命令本体。

### 14.5 心智模型补完

原三个概念（Command / CommandRegistry / Frontend）不变，仅补一句：

> 一个 `Session` 可被多个 `Frontend` 同时挂载；命令执行的"流"由 `Session` 唯一持有，输出由 `OutputMultiplexer` 广播到所有挂载的 `OutputSink`。

这把 §2 的心智模型从"单前端"自然泛化为"多前端共享会话"，仍只增加一个概念（Session 的共享语义），复杂度可控。

以上为方案主体。评审通过后按此落地实现，实现同样保持零依赖。
