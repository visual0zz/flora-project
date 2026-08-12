# CLI / TUI 通用命令框架设计方案

日期：2026-08-07
修订：2026-08-12（概念收敛：前端降级为输入源/输出汇与入口壳；终态生命周期）
状态：方案稿（待评审）

## 1. 背景与目标

项目内 `flora-osmetes`、`flora-ramet`、`flora-tangle` 的命令行入口全部是手写位置参数解析 + `System.out` 打印，重复度高、无统一帮助、无法复用。同时 AI Agent 需要把工具以结构化方式暴露，未来还可能有交互式工具（类 tmux、类 vim）。本项目当前没有任何 CLI 参数解析框架、ANSI 渲染层或 TUI 代码。

本框架的目标：

- 一套命令定义，同时服务于 **CLI（一次性执行）**、**TUI（常驻交互）**、**AI Agent（结构化调用）**、**GUI（业务自持）** 四种接入形态；
- 命令类**自描述**：名称、参数、帮助都聚合在命令类内部，help 由聚合层统一生成，而非散落各处的字符串；
- 允许"一处定义共享"，也允许**按接入形态特化**（某个命令只给 TUI 定义交互视图、只给 Agent 定义返回 schema）；
- **零第三方依赖**；
- 心智模型尽量小：一个引擎（Session）+ 两类挂件（输入源、输出汇）+ 入口壳。

## 2. 心智模型

> 一个命令 = 一个类。命令类自己说出"我叫什么、要什么参数、做什么、帮助是什么"。
> 一个 Session = 唯一的执行引擎。输入源把"一次调用"喂进来，输出汇把"执行结果"广播出去。
> 入口壳（CLI main / GUI 适配器）只是薄壳，不是框架概念。

三个核心概念：

1. **Command（命令）**——自描述的执行单元，一个类一个命令。
2. **CommandRegistry（注册表）**——命令的集合，负责注册、分派与 help 聚合。
3. **Session（会话）**——常驻执行引擎：持有状态、串行执行、汇聚所有输入源与输出汇。

以及两个"挂件"概念（非核心，但构成完整心智模型）：

- **InputSource（输入源）**——键盘、微信长连接……任何能产生"一次输入"的东西，统一归一化为 `InputEvent` 提交给 Session（argv 不是输入源：它由入口壳一次性消费，不经 attach）。
- **OutputSink（输出汇）**——屏幕、微信连接、stdout……Session 通过 `OutputMultiplexer` 把执行结果扇出到所有已挂载的输出汇。

一次输入到一次执行的完整管线：

```
InputSource（键盘 / 微信 / 命令面板）
  → InputEvent（来源 + 命令调用描述）
  → Session.submit（串行队列）
  → 分词 + CommandRegistry 查找 + ArgParser 校验（→ ParsedArgs）
  → Invocation（命令 + ParsedArgs + Session + Output）
  → execute → CommandResult
  → OutputMultiplexer 广播到所有 OutputSink
```

书写一个新命令的默认路径只有四步（回答四个问题）：

```
叫什么（name） → 做什么（description） → 收什么参数（args） → 收到后干嘛（execute）
```

四个问题全部写在同一个类里。做到这四步，CLI、TUI、Agent、GUI 四种接入形态开箱即用；TUI 提供通用的参数表单渲染。只有需要**专属交互**（vim 的按键模式、tmux 的窗格操作）时，命令才额外实现特化接口——这是可选的增量，不是默认负担。

### 2.1 接入形态（入口壳）

框架不把"CLI / GUI"建模为前端，它们只是把用户带进 Session 的入口壳。CLI 与 TUI 是**同一个入口的两条结局**（argv 执行后是否驻留），不是两个入口：

| 入口壳 | 进入方式 | 生命周期 | 驻留 |
|---|---|---|---|
| CLI | `CliEntry.main`：解析 argv → 命令序列 → dispatch → 读退出码 → 退出 | 一次性 | 否 |
| TUI | 同一 main：argv 执行完后若请求了驻留（`tui`）则进入 Session 交互循环 | 常驻 | 是 |
| GUI | 业务实现 `GuiAdapter` SPI，框架经 `gui` 命令调用 | 业务自持 | 业务决定 |

库形态（不是入口壳）：

| 形态 | 进入方式 |
|---|---|
| Agent | 宿主代码直接调 `session.call(...)`（JSON 归一化后走同一分派与校验），Hanako 这类引擎以库形态接入 |

**驻留是 Session 的属性**：main 线程跑 Session 的循环；循环没有交互输入源且无人请求驻留时立即返回（一次性场景自然退出）。`tui` 命令请求驻留；`exit` 命令结束驻留并携带退出码。`tui`/`gui`/`exit` 都是普通命令，没有"按前端可见性过滤"——语义在各形态下自然退化（`tui` 在 TUI 里是 no-op；`exit` 在 CLI 一次性场景就是提前退出）。

## 3. 模块与包结构

推荐放入 `flora-root`（零依赖工具库，所有工具模块已依赖它），顶层包 `com.flora.shell`。
备选：独立 `flora-shell` 模块（若担心 flora-root 继续膨胀；代价是其他模块需新增依赖）。

```
com.flora.shell
├── Command                  # 命令接口（声明 + 执行 + 特化入口）
├── CommandRegistry          # 注册表：注册、查找、分派、聚合 help、内置指令注册
├── Invocation               # 一次命令调用上下文（含 Session、Output）
├── CommandResult            # 执行结果（退出码 / 结构化数据 / 驻留请求）
├── Session                  # 执行引擎：状态、串行执行、输入源/输出汇挂载
├── builtin/                 # 内置指令（预制）：help / tui / gui / exit
├── input/
│   ├── InputSource          # 输入源接口
│   ├── InputEvent           # 归一化输入事件（来源 + 命令调用描述：文本或结构化）
│   └── KeyInputSource       # 键盘输入源（TUI 使用）
├── output/
│   ├── OutputSink           # 输出汇接口
│   ├── OutputMultiplexer    # 扇出器（实现 Output，广播到所有 sink）
│   └── ScreenSink           # 写 TUI 屏幕的输出汇
├── entry/
│   ├── CliEntry             # CLI 入口壳：argv → dispatch → 退出码
│   └── GuiAdapter           # GUI 适配 SPI：业务实现 launch(GuiContext)；同名多实现按 priority() 裁决
├── spec/
│   ├── ArgSpec              # 参数/选项声明（声明式，非解析代码）
│   ├── ParsedArgs           # 解析结果
│   └── ArgParser            # 零依赖解析器（cli 串 / 列表 / JSON 输入）
├── help/
│   ├── HelpProvider         # 命令向 help 聚合层提供数据
│   └── HelpRenderer         # 渲染成文本树 / 分屏 / Agent 工具描述
└── tui/                     # TUI 原语（零依赖 ANSI 实现）
    ├── RawTerminal          # 原始模式开关
    ├── KeyEvent             # 按键事件
    ├── ScreenBuffer         # 离屏缓冲 + ANSI 差分刷新
    └── Layout               # 窗格/布局（split、状态栏、命令面板）
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
    int priority();                     // 默认 0；内置指令为负，用户命令可覆写内置指令

    // —— 执行层：一次调用 ——
    CommandResult execute(Invocation ctx) throws Exception;
}
```

- `name()` 支持点分路径，天然表达**子命令树**（`session.new`、`buffer.write`），注册表按前缀聚合为命令树。
- `args()` 是**声明**不是代码：解析、help 生成、Agent 的 JSON schema 都从这同一份声明推导，保证"一处定义、处处一致"。
- `execute()` 不直接碰 `System.out`，而是通过 `Invocation` 里的 `Output` 写输出，因此同一份实现可同时跑在 CLI 打印、TUI 面板与微信回写上。
- `priority()` 只用于**同名冲突裁决**：内置指令（help/tui/gui/exit）以低优先级注册，用户定义同名命令可覆写内置指令；用户命令之间同名冲突直接抛异常（视为 bug），不裁决。

### 4.1 内置指令（预制）

| 指令 | 行为 | 语义 |
|---|---|---|
| `help [cmd]` | 遍历注册表，按点分树渲染帮助 | 自举：help 自身也是命令 |
| `tui` | 请求驻留：当前命令序列执行完毕后进入交互循环（非阻塞标志） | 无交互输入源在场时报错，避免进程挂死 |
| `gui [--flavor name]` | 经 SPI 发现 `GuiAdapter`，调用其 `launch(ctx)`（阻塞，返回即终态结束） | 多实现按名字/优先级裁决 |
| `exit [code]` | 结束驻留并携带退出码（默认 0） | 终态形态下即进程退出 |

`tui` 与 `gui` 的驻留机制不同：`tui` 必须是非阻塞的"序列结束后兑现"标志——交互循环需要 dispatch 能力，若在 `execute` 内阻塞会造成 dispatch 内部自递归；`gui` 是阻塞调用——业务 GUI 自持事件循环线程，`launch(ctx)` 返回后进程随之退出。

### 4.2 接入形态特化（可选增量）

命令类可以额外实现以下接口，按需提供专属行为：

```
interface CliView {      // 一次性入口专属：argv 级前置校验 / 定制错误输出，默认由框架提供
    default String beforeExecute(List<String> rawArgs) { ... }  // 返回 null 或错误
}
interface TuiView {      // TUI 专属：按键绑定、会话内视图
    default void bindKeys(KeyMap km) { }
    default View view(Session s) { return null; }   // null = 用通用表单
}
interface AgentView {    // Agent 专属：定制工具描述 / 返回值 schema，默认由声明自动生成
    default ToolSchema toolSchema() { ... }
}
interface SourceRestricted { // 来源限制：声明允许触发本命令的输入源白名单（如仅键盘、不允许微信）
    default Set<String> allowedSources() { return Set.of(); }  // 空 = 不限制
}
```

心智模型规则：**"不实现任何特化接口"= 各形态通用；"实现某个特化接口"= 只对该形态生效，其他形态仍走默认。** 特化只针对行为，不复制声明——声明永远只有 `Command` 接口那一份。

## 5. 参数声明与解析

`ArgSpec` 声明式描述参数，覆盖所有输入形态（CLI argv、TUI 命令面板、Agent JSON）：

- **选项**：`--name` / `-n`（可带值、可重复、可 bool 开关）
- **位置参数**：`<src>`（可选/必选/变长）
- **值约束**：类型（int/str/enum/list）、默认值、合法枚举、数值范围
- **组合规则**：互斥、必选其一（由解析器校验）

解析器只有一个入口，`ParsedArgs parse(List<String> argv)`；AI Agent 输入（JSON 对象）先归一化成同一种 `Map<String,Object>` 再走同一校验，从而 **CLI 的 `--port 8080` 与 Agent 的 `{"port":8080}` 落到同一套校验逻辑**。

解析失败时返回结构化的错误（缺哪个参数、哪个值非法、期望什么），由各形态按自己的方式呈现：CLI 打印到 stderr 并置非零退出码，TUI 画到命令面板，Agent 回传错误对象。

## 6. Help 聚合到命令类内部

设计原则：**命令类是 help 的唯一事实来源**。`Command` 的声明层（name/description/args/usage）就是 help 数据，不需要单独维护一份帮助字符串。

- 单命令 help：由命令类声明 + 示例段自动渲染。
- 全局 help：`CommandRegistry` 遍历所有命令，按点分名构建命令树，`HelpRenderer` 渲染：
  - **CLI**：`help [cmd]` 或 `cmd --help` → 文本树打印（`--help` 由 registry 在分派前拦截并转为 help 调用，命令无需自处理；两条入口映射到同一逻辑）；
  - **TUI**：帮助页以分屏/滚动视图展示；
  - **Agent**：会话初始化时框架直接把整棵命令树转成工具列表描述，随 `toolSchema` 交给模型（Agent 形态下 `help` 命令返回结构化结果，而非文本）。
- 聚合顺序与层级由注册表保证，命令之间无感知，新增命令自动出现在全局 help 中，无需改动聚合代码。

## 7. 会话、输入源与输出汇（原"前端抽象"）

### 7.1 Session

Session 是唯一执行引擎：

- 持有常驻状态（光标、当前文件、窗格树、退出码）；
- 串行执行：所有输入源的调用排队执行（单执行锁或单线程事件循环），避免抢状态；
- 执行管线：`submit(InputEvent)` 入队 → 经 `CommandRegistry` 分派（查找命令 + `ArgParser` 校验）→ 构造 `Invocation` 执行 → 结果经 `OutputMultiplexer` 广播（§7.2）；
- 挂载输入源与输出汇：`attach(InputSource)` / `attach(OutputSink)`。

### 7.2 输入归一化与执行管线

一次输入到一次执行的完整管线（与 §2 相同，此处展开细节）：

```
InputSource（键盘 / 微信 / 命令面板）
  → InputEvent（来源 + 命令调用描述）
  → Session.submit（串行队列）
  → 分词 + CommandRegistry 查找 + ArgParser 校验（→ ParsedArgs）
  → Invocation（命令 + ParsedArgs + Session + Output）
  → execute → CommandResult
  → OutputMultiplexer 广播到所有 OutputSink
```

`InputEvent` 统一两种输入：**文本命令**（微信、argv、命令面板输入，先分词成 argv 再走 `ArgParser.parse`）与**结构化调用**（快捷键经 `KeyMap` 直接绑定为"命令名 + 参数 Map"，与 Agent 的 JSON 归一到同一种 `Map<String,Object>` 后走同一校验）。归一化在"进 Session 之前"完成，命令永远只看到统一的 `Invocation`。

### 7.3 输出扇出

```
interface OutputSink { void emit(String text); void emitError(String text); }
interface Output { void print(String s); void println(String s); void error(String s); }
// OutputMultiplexer 实现 Output，把每次调用 fan-out 到所有已挂载的 OutputSink
class OutputMultiplexer implements Output {
    void attach(OutputSink s);   // 键盘壳挂 ScreenSink，微信挂 WeChatSink
}
```

`Invocation.out()` 返回的是 `OutputMultiplexer`：命令只管 `out().println(...)`，所有挂载的输出汇都收到。`OutputSink` 是 `Output` 的降维接口：`emit(text)` 对应 `print`/`println`（`println` 由扇出层补换行），`emitError` 对应 `error`，业务输出汇只需实现两个方法。

### 7.4 入口壳与驻留

- `CliEntry.main`：解析 argv → dispatch 命令序列 → 若序列请求了驻留（`tui`）则进入 Session 交互循环，否则读退出码直接退出。
- `KeyInputSource` 由 `CliEntry` 启动时按 TTY 检测决定是否 attach（与驻留解耦），因此 `tui` 的"交互输入源在场"检查在进入循环前即成立。
- `tui` 请求驻留时要求至少一个交互输入源在场（键盘），否则报错——避免在无 TTY 环境挂死。
- `exit` 设置 Session 退出码并结束循环；终态形态下进程以该码退出。
- GUI：`GuiAdapter.launch(ctx)` 内运行业务自己的事件循环，把 GUI 事件喂给 Session、从 Session 收输出；GUI 结束时把结果告知框架（`ctx.finish(code)`），进程随之退出。

## 8. TUI 原语（零依赖 ANSI）

TUI 不依赖任何第三方库，直接用 ANSI 转义序列实现四件套：

- **RawTerminal**：Unix 用 `ProcessBuilder` 调 `stty raw -echo`（进入）与 `stty sane`（恢复），进程退出钩子保证恢复；**Windows 经 FFM 实现零依赖全支持**——`flora-root` 的 `com.flora.os.natives.ffm.NativeLib` 用 JDK 标准 FFM 直接 downcall `kernel32` 的 `GetStdHandle`/`GetConsoleMode`/`SetConsoleMode` 切换 raw mode，无需 JNA/JNI。完整实现与代码见技术探索笔记 `addition/exploration/explore20260807-windows-raw-mode-ffm.md`。使用前提：启动 JVM 需 `--enable-native-access=com.flora.root`。注意经典 Conhost 与 Windows Terminal 的 ConPTY 在 raw 语义上略有差异；该方案覆盖经典控制台场景。
- **KeyEvent**：从原始字节流解析按键（普通键、方向键、Ctrl 组合、Fn），跨终端映射到统一键名，供 `KeyMap` 绑定。
- **ScreenBuffer**：离屏字符缓冲 + 全量 diff，每次渲染只输出变化区域，避免整屏闪烁。
- **Layout**：窗格树（split 上下/左右）、状态栏、命令输入面板。类 tmux 的窗格操作、类 vim 的编辑区/命令行区分工都由这一层承载。

现有 `com.flora.os.shell.color`（ANSI 颜色/样式常量：`AnsiConsole`、`ShellColor`、`ShellStyle` 等，当前未导出未使用）可迁移进 `tui/` 作为颜色基元，顺带解决其"定义即闲置"的状态。

## 9. 场景映射

| 场景 | 接入形态 | 典型命令 | 特化点 |
|---|---|---|---|
| AI Agent | Agent（结构化调用） | `osmetes.check`、`ramet.gen` | 自动生成工具 schema 与 JSON 结果；需要机器可读返回时实现 `AgentView` |
| 普通 shell 指令 | CLI 入口壳 | 现有三个模块的入口收编 | 默认即用 |
| 类 tmux | TUI | `session.new`、`pane.split`、`pane.kill` | `TuiView.bindKeys` 绑窗格快捷键，`Layout` 管窗格树 |
| 类 vim | TUI | `buffer.write`、`search.next` | 命令执行中持有"会话状态"（当前文件/光标），模式切换由 `bindKeys` 表达 |
| 业务 GUI | GuiAdapter SPI | `gui --flavor javafx` | 业务实现自己的 UI 与事件循环 |

所有场景**共享**：命令声明、参数解析、help 聚合、注册表、Session。**差异**：仅入口壳、输入源/输出汇的挂载，与可选的命令特化层。

## 10. 扩展机制

新增一种接入形态时：

1. 若是"一次性/常驻文本"形态：复用 `CliEntry`（是否驻留由命令决定）；
2. 若是业务 UI：实现一个 `GuiAdapter`（SPI 注册），框架经 `gui` 命令发现并调用；
3. 若是新的输入渠道（如微信）：实现 `InputSource` + 对应 `OutputSink`，attach 到 Session。

新增命令时，只需要：写一个 `Command` 类 + 一行注册。`CommandRegistry` 提供注册、按名查找、解析调用、生成 help 树四个能力，命令间完全解耦。

## 11. 零依赖策略与已知难点

- **ANSI 渲染 / 屏幕缓冲 / 布局**：纯 JDK 可完成，无难点。
- **参数解析**：手写状态机即可，本项目入口现状已证明规模可控。
- **原始模式**（TUI 专属）：Unix 借 `stty` 子进程实现零依赖；Windows 经 FFM downcall `kernel32` 实现（见 §8）。
- **GUI 适配 SPI**：JPMS 标准机制——业务模块声明 `provides com.flora.shell.entry.GuiAdapter`，框架模块声明 `uses`；`gui` 命令经 `ServiceLoader` 发现实现。

## 12. 与现有代码的关系（收编路径）

- `OsmetesCli`、`Ramet`、`Tangle` 的入口改为"命令类 + `CliEntry`"，手写参数循环替换为 `ArgSpec` 声明，帮助文本由声明生成，行为不变、代码量下降。
- `com.flora.os.shell.color` 迁入 `tui/` 复用。
- `flora-root/module-info.java` 需新增 `exports com.flora.shell` 及子包导出（若采用 §3 推荐方案）。

## 13. 决策点与开放问题

1. **放置位置**：flora-root（推荐）还是独立 flora-shell 模块——需用户确认。
2. **命令命名**：点分路径（推荐）与嵌套 `CommandGroup` 对象两种表达子命令树的方式，前者更贴合"心智模型简单"，后者层级能力更强。
3. **参数校验错误模型**：统一"结构化的参数错误"是否也用于 CLI 的 stderr 展示格式，待定。
4. **输入归一化位置**：定于"进 Session 之前"（§7.2），`InputEvent` 的具体形态（是否复用 `ParsedArgs` 构造路径）待定。
5. **执行串行化**：Session 内置单执行锁还是单线程事件循环，锁粒度与超时策略待定。
6. **GUI 多实现裁决**：多个 `GuiAdapter` 同名时按其自述 `priority()` 裁决（语义复用 `com.flora.common.register` 的裁决思想），`--flavor` 选择具体实现，细节待定。
7. **内置指令覆写规则**：用户命令覆写内置指令是否要显式声明（如实现某个标记接口）还是仅靠 `priority()`，待定。

## 14. 多输入源共享 Session（微信 + TUI 融合场景）

**需求**：一个 Agent TUI 连接微信，微信发来的消息与本地键盘输入"汇总到同一个流"一起执行、一起显示——类比一个 tmux session 被两个 ssh 同时访问。

**结论**：框架原生支持，且**命令声明层零改动**。本质是给同一个 `Session` 挂上两个输入源（键盘、微信）与两个输出汇（屏幕、微信连接），命令结果经 `OutputMultiplexer` 广播。

### 14.1 拓扑对应（tmux 多 client 模型）

| tmux | 框架 | 说明 |
|---|---|---|
| session | `Session` | 常驻状态（光标、当前文件、窗格树）——**唯一** |
| client (ssh) | `InputSource` + `OutputSink` | 输入渠道 + 输出目的地——**可多个** |
| 共享输出缓冲 | `OutputMultiplexer` | 广播到所有已挂载 sink |

### 14.2 融入方式（三处都在框架边界，不碰命令）

1. **微信 = 一个 `InputSource` + 一个 `OutputSink`**：实现 `InputSource`（长连接读消息 → 归一化 `InputEvent`）与 `OutputSink`（回写微信连接），attach 到 Session。命令代码对此完全无感知。
2. **输出扇出**：`OutputMultiplexer` 下挂 `ScreenSink`（写 TUI 屏）与 `WeChatSink`（回写微信连接），命令只管 `out().println(...)`，各 sink 都收到，自然"一起显示"。
3. **Session 是执行流的汇合点**：命令从 `Invocation.session()` 取状态、写回状态；两个输入源共用它，结果即共享上下文。这也解释了为什么"命令层零改动"：命令仍只声明 `args/usage/execute`，不感知输入来自键盘还是微信。

### 14.3 需要补强的两块框架基础设施

- **输入事件归一化**（对应 §13 开放问题 4）：键盘产生 `KeyEvent`，微信消息是字符串，统一成 `InputEvent` 后进 Session（§7.2），否则各输入源各自解析会分叉。
- **执行串行化**（对应 §13 开放问题 5）：两个输入源同时发命令会抢 Session 状态，Session 内置单执行锁（或单线程事件循环），命令排队执行——这正是 tmux 多 client 共享 session 时的内在约束。锁的粒度与超时策略是待定实现细节。

### 14.4 命令层零改动原则重申

这是关键收益：你写的 `Command`（`name()/args()/usage()/execute()`）完全不知道输入来自键盘还是微信。若某些指令需限制来源（如 `tui` 驻留请求不允许微信触发），在特化接口 `SourceRestricted` 声明来源白名单，仍是"一处定义、按需特化"，不污染命令本体。

### 14.5 心智模型检查

本场景不需要新增任何概念：微信只是又一对 `InputSource` + `OutputSink`，与键盘、屏幕地位相同。Session 的串行执行与输出扇出（§7）天然支持多输入源共享，§2 的心智模型原样适用。

以上为方案主体。评审通过后按此落地实现，实现同样保持零依赖。
