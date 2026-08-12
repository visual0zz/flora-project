# CLI / TUI 通用命令框架设计方案

日期：2026-08-07
修订：2026-08-12（概念收敛：前端降级为渠道与入口壳；终态生命周期；组件化：干净的指令组件 + TUI 组件 + 多渠道）
状态：方案稿（待评审）

## 1. 背景与目标

项目内 `flora-osmetes`、`flora-ramet`、`flora-tangle` 的命令行入口全部是手写位置参数解析 + `System.out` 打印，重复度高、无统一帮助、无法复用。同时 AI Agent 需要把工具以结构化方式暴露，未来还可能有交互式工具（类 tmux、类 vim）。本项目当前没有任何 CLI 参数解析框架、ANSI 渲染层或 TUI 代码。

本框架的目标：

- 一套命令定义，同时服务于 **批量调用（一次性执行）**、**AI Agent（结构化调用）**、**交互式 TUI**、**GUI（业务自持）**，以及多渠道融合（如微信 + TUI）；
- 命令类**自描述**：名称、参数、帮助都聚合在命令类内部，help 由聚合层统一生成，而非散落各处的字符串；
- 允许"一处定义共享"，也允许**按接入方式特化**（某个命令只给交互会话定义交互视图、只给 Agent 定义返回 schema）；
- **零第三方依赖**；
- 心智模型尽量小：一个干净的指令组件（路由 + 执行 + 输出扇出），TUI、GUI、渠道都是可选的挂件。

## 2. 心智模型

> 一个命令 = 一个类。命令类自己说出"我叫什么、要什么参数、做什么、帮助是什么"。
> 一个指令组件 = 干净的路由与执行：注册命令、串行分派、输出扇出。它无状态、无 UI、不拥有输入源、不管生命周期。
> TUI、微信、GUI 都是可选的"挂件"——它们把输入变成调用提交给组件，从组件拿到输出。

核心概念：

1. **Command（命令）**——自描述的执行单元，一个类一个命令。
2. **CommandComponent（指令组件）**——注册、串行分派、输出扇出；零状态，可独立工作（批量、Agent 只用它）。
3. **TuiComponent（TUI 组件）**——交互界面：键盘输入源、光标/窗格/布局、屏幕渲染；构造时注入一个指令组件负责执行命令；提供可注册的 `tui` 指令。

以及两个"挂件"概念（非核心，但构成完整心智模型）：

- **渠道（Channel）**——任何能产生"一次调用"的东西（键盘、微信长连接、argv）。渠道把输入归一化为 `InputEvent` 提交给组件，需要回写时挂自己的 `OutputSink`。
- **OutputSink（输出汇）**——屏幕、微信连接、stdout……组件通过 `OutputMultiplexer` 把执行结果扇出到所有已挂载的输出汇。

一次输入到一次执行的完整管线：

```
渠道（键盘 / 微信 / argv）
  → InputEvent（来源 + 命令调用描述）
  → CommandComponent.submit（串行队列）
  → 分词 + 查找命令 + ArgParser 校验（→ ParsedArgs）
  → Invocation（命令 + ParsedArgs + Output + 调用方状态）
  → execute → CommandResult
  → OutputMultiplexer 扇出到所有 OutputSink
```

书写一个新命令的默认路径只有四步（回答四个问题）：

```
叫什么（name） → 做什么（description） → 收什么参数（args） → 收到后干嘛（execute）
```

四个问题全部写在同一个类里。做到这四步，批量、Agent、GUI、TUI、微信融合全部可用；TUI 提供通用的参数表单渲染。只有需要**专属交互**（vim 的按键模式、tmux 的窗格操作）时，命令才额外实现特化接口——这是可选的增量，不是默认负担。

### 2.1 接入方式

| 接入方式 | 组成 | 生命周期 |
|---|---|---|
| 批量 | `Entry` + 指令组件 | 一次性（argv → 执行 → 退出码） |
| Agent | 指令组件（库形态，结构化调用） | 按会话多次调用 |
| TUI | 指令组件 + `TuiComponent`（`tui` 指令） | 进入交互循环，`exit` 结束 |
| GUI | 指令组件 + `GuiAdapter` | 业务自持 |
| 微信融合 | 指令组件 + `TuiComponent` + 微信渠道 | 同 TUI |

框架不提供驻留逻辑：`Entry` 执行完即退出；进入交互是显式的——把 `TuiComponent` 提供的 `tui` 指令注册进外层组件后，`flora-tool ... tui` 即进入交互循环，`exit` 结束。未注册 `tui` 的工具（如收编的批量工具）永远不会进入交互，无参数调用默认报错（帮助到 stderr、非零退出码）。

## 3. 模块与包结构

推荐放入 `flora-root`（零依赖工具库，所有工具模块已依赖它），顶层包 `com.flora.shell`。
备选：独立 `flora-shell` 模块（若担心 flora-root 继续膨胀；代价是其他模块需新增依赖）。

```
com.flora.shell
├── Command                  # 命令接口（声明 + 执行 + 特化入口）
├── CommandComponent         # 指令组件：注册、串行分派、输出扇出（零状态、无 UI）
├── Invocation               # 调用上下文：命令 + 参数 + Output + 调用方状态（组件透传）
├── InputEvent               # 归一化输入（来源 + 命令调用描述：文本或结构化）
├── CommandResult            # 执行结果（退出码 / 结构化数据）
├── builtin/                 # 内置指令（预制）：help / gui
├── spec/
│   ├── ArgSpec              # 参数/选项声明（声明式，非解析代码）
│   ├── ParsedArgs           # 解析结果
│   └── ArgParser            # 零依赖解析器（cli 串 / 列表 / JSON 输入）
├── help/
│   ├── HelpProvider         # 命令向 help 聚合层提供数据
│   └── HelpRenderer         # 渲染成文本树 / 分屏 / Agent 工具描述
├── output/
│   ├── OutputSink           # 输出汇接口
│   └── OutputMultiplexer    # 扇出器（实现 Output，广播到所有 sink）
├── entry/
│   ├── Entry                # 一次性入口壳：argv → submit → 退出码
│   └── GuiAdapter           # GUI 适配 SPI：业务实现 launch(GuiContext)；同名多实现按 priority() 裁决
└── tui/                     # TUI 组件与渲染原语（零依赖 ANSI 实现）
    ├── TuiComponent         # TUI 组件：注入 CommandComponent；键盘/光标/布局/屏幕；提供 tui 指令
    ├── KeyInputSource       # 键盘输入源（TUI 持有）
    ├── ScreenSink           # 屏幕输出汇（TUI 持有）
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

- `name()` 支持点分路径，天然表达**子命令树**（`session.new`、`buffer.write`），组件按前缀聚合为命令树。
- `args()` 是**声明**不是代码：解析、help 生成、Agent 的 JSON schema 都从这同一份声明推导，保证"一处定义、处处一致"。
- `execute()` 不直接碰 `System.out`，而是通过 `Invocation` 里的 `Output` 写输出，因此同一份实现可同时跑在批量打印、TUI 面板与微信回写上。
- `priority()` 只用于**同名冲突裁决**：内置指令（help/gui）以低优先级注册，用户定义同名命令可覆写内置指令；用户命令之间同名冲突直接抛异常（视为 bug），不裁决。
- **命令应无状态**：状态一律放调用方传入的 `Invocation.state()`（或组件外的领域对象），保证同一命令在多个组件中重复注册（各 `registerBySpi()`）时行为一致。

### 4.1 内置指令（预制）

| 指令 | 提供方 | 行为 |
|---|---|---|
| `help [cmd]` | 组件 | 遍历注册表，按点分树渲染帮助（自举） |
| `gui [--flavor name]` | 组件 | 经 SPI 发现 `GuiAdapter`，调用其 `launch(ctx)`（阻塞，返回即终态结束） |
| `tui` | TuiComponent | 进入交互循环：`TuiComponent(inner).run()`，`exit` 结束 |
| `exit` | TuiComponent | 结束交互循环（由 TuiComponent 注入内层组件） |

`tui` 与 `exit` 由 `TuiComponent` 提供，用户把它们注册进外层/内层组件；不注册 = 该工具不支持交互。

### 4.2 接入方式特化（可选增量）

命令类可以额外实现以下接口，按需提供专属行为：

```
interface CliView {      // 批量入口专属：argv 级前置校验 / 定制错误输出，默认由框架提供
    default String beforeExecute(List<String> rawArgs) { ... }  // 返回 null 或错误
}
interface TuiView {      // TUI 专属：按键绑定、会话内视图
    default void bindKeys(KeyMap km) { }
    default View view(TuiComponent tui) { return null; }   // null = 用通用表单
}
interface AgentView {    // Agent 专属：定制工具描述 / 返回值 schema，默认由声明自动生成
    default ToolSchema toolSchema() { ... }
}
interface SourceRestricted { // 来源限制：声明允许触发本命令的渠道白名单（如 `gui`/`exit` 不允许微信触发）
    default Set<String> allowedSources() { return Set.of(); }  // 空 = 不限制
}
```

心智模型规则：**"不实现任何特化接口"= 各接入方式通用；"实现某个特化接口"= 只对该方式生效，其他方式仍走默认。** 特化只针对行为，不复制声明——声明永远只有 `Command` 接口那一份。

## 5. 参数声明与解析

`ArgSpec` 声明式描述参数，覆盖所有输入方式（argv、TUI 命令面板、Agent JSON）：

- **选项**：`--name` / `-n`（可带值、可重复、可 bool 开关）
- **位置参数**：`<src>`（可选/必选/变长）
- **值约束**：类型（int/str/enum/list）、默认值、合法枚举、数值范围
- **组合规则**：互斥、必选其一（由解析器校验）

解析器只有一个入口，`ParsedArgs parse(List<String> argv)`；AI Agent 输入（JSON 对象）先归一化成同一种 `Map<String,Object>` 再走同一校验，从而 **CLI 的 `--port 8080` 与 Agent 的 `{"port":8080}` 落到同一套校验逻辑**。

解析失败时返回结构化的错误（缺哪个参数、哪个值非法、期望什么），由各接入方式按自己的方式呈现：批量调用打印到 stderr 并置非零退出码，TUI 画到命令面板，Agent 回传错误对象。

## 6. Help 聚合到命令类内部

设计原则：**命令类是 help 的唯一事实来源**。`Command` 的声明层（name/description/args/usage）就是 help 数据，不需要单独维护一份帮助字符串。

- 单命令 help：由命令类声明 + 示例段自动渲染。
- 全局 help：组件遍历所有命令，按点分名构建命令树，`HelpRenderer` 渲染：
  - **批量**：`help [cmd]` 或 `cmd --help` → 文本树打印（`--help` 由组件在分派前拦截并转为 help 调用，命令无需自处理；两条入口映射到同一逻辑）；
  - **TUI**：帮助页以分屏/滚动视图展示；
  - **Agent**：会话初始化时宿主直接把整棵命令树转成工具列表描述，随 `toolSchema` 交给模型。
- 聚合顺序与层级由组件保证，命令之间无感知，新增命令自动出现在全局 help 中，无需改动聚合代码。

## 7. 指令组件、渠道与执行管线

### 7.1 CommandComponent（指令组件）

指令组件是**干净的路由与执行单元**，可独立工作（批量、Agent 只用它）：

- **注册**：`register(Command)` / `registerBySpi()`；
- **分派**：`submit(InputEvent, state)`——串行队列执行（多渠道并发提交安全），查找命令 + `ArgParser` 校验 → 构造 `Invocation` → `execute`；
- **输出扇出**：`attach(OutputSink)`，执行结果经 `OutputMultiplexer` 广播；
- **零状态、无 UI、不拥有输入源、不管生命周期**。

### 7.2 执行管线

```
渠道（键盘 / 微信 / argv）
  → InputEvent（来源 + 命令调用描述）
  → CommandComponent.submit（串行队列）
  → 分词 + 查找命令 + ArgParser 校验（→ ParsedArgs）
  → Invocation（命令 + ParsedArgs + Output + 调用方状态）
  → execute → CommandResult
  → OutputMultiplexer 扇出到所有 OutputSink
```

`InputEvent` 统一两种输入：**文本命令**（微信、argv、TUI 命令面板，先分词成 argv 再走 `ArgParser.parse`）与**结构化调用**（快捷键经 `KeyMap` 直接绑定为"命令名 + 参数 Map"，与 Agent 的 JSON 归一到同一种 `Map<String,Object>` 后走同一校验）。归一化由各渠道自行完成（或复用框架提供的解析工具），组件只接收归一化后的 `InputEvent`。

### 7.3 输出扇出

```
interface OutputSink { void emit(String text); void emitError(String text); }
interface Output { void print(String s); void println(String s); void error(String s); }
// OutputMultiplexer 实现 Output，把每次调用 fan-out 到所有已挂载的 OutputSink
class OutputMultiplexer implements Output {
    void attach(OutputSink s);   // TUI 挂 ScreenSink，微信挂 WeChatSink
}
```

`Invocation.out()` 返回的是 `OutputMultiplexer`：命令只管 `out().println(...)`，所有挂载的输出汇都收到。`OutputSink` 是 `Output` 的降维接口：`emit(text)` 对应 `print`/`println`（`println` 由扇出层补换行），`emitError` 对应 `error`，业务输出汇只需实现两个方法。

### 7.4 状态与上下文

组件不持有任何状态。状态按归属分家：

- **UI 状态**（光标、窗格、布局、当前视图）——归 `TuiComponent`；
- **领域状态**（当前文件、buffer、打开的会话）——归调用方（TUI/用户代码）持有；
- `Invocation.state()` 是调用方传入的对象，组件原样透传，命令侧通过它读写领域状态。

### 7.5 入口壳（一次性执行）

- `Entry.run(component, args)`：解析 argv → 逐个转成 `InputEvent` 提交 → 执行完读退出码退出。框架不提供驻留逻辑。
- argv 为空：默认报错（帮助渲染到 stderr、非零退出码），与现有工具"无参数打印用法退出"的行为一致；需要"无参数进入交互"的工具由宿主自行决定（如直接调 `tui`）。
- GUI：`GuiAdapter.launch(ctx)` 内运行业务自己的事件循环，把 GUI 事件喂给组件、从组件收输出；GUI 结束时把结果告知框架（`ctx.finish(code)`），进程随之退出。

## 8. TUI 组件与渲染原语（零依赖 ANSI）

### 8.1 TuiComponent

TUI 组件 = 交互界面的所有者，构造时注入一个 `CommandComponent` 负责执行命令：

- **持有**：键盘输入源（`KeyInputSource`）、光标/窗格/布局状态、屏幕输出汇（`ScreenSink`）、领域状态；
- **循环**：读取键盘/其他输入 → 构造 `InputEvent`（带上领域状态）→ `component.submit(...)` → 结果渲染到屏幕；
- **提供指令**：`tui`（注册进外层组件，`run()` 阻塞至 `exit`）与 `exit`（注入内层组件，结束循环）。

### 8.2 渲染原语

- **RawTerminal**：Unix 用 `ProcessBuilder` 调 `stty raw -echo`（进入）与 `stty sane`（恢复），进程退出钩子保证恢复；**Windows 经 FFM 实现零依赖全支持**——`flora-root` 的 `com.flora.os.natives.ffm.NativeLib` 用 JDK 标准 FFM 直接 downcall `kernel32` 的 `GetStdHandle`/`GetConsoleMode`/`SetConsoleMode` 切换 raw mode，无需 JNA/JNI。完整实现与代码见技术探索笔记 `addition/exploration/explore20260807-windows-raw-mode-ffm.md`。使用前提：启动 JVM 需 `--enable-native-access=com.flora.root`。注意经典 Conhost 与 Windows Terminal 的 ConPTY 在 raw 语义上略有差异；该方案覆盖经典控制台场景。
- **KeyEvent**：从原始字节流解析按键（普通键、方向键、Ctrl 组合、Fn），跨终端映射到统一键名，供 `KeyMap` 绑定。
- **ScreenBuffer**：离屏字符缓冲 + 全量 diff，每次渲染只输出变化区域，避免整屏闪烁。
- **Layout**：窗格树（split 上下/左右）、状态栏、命令输入面板。类 tmux 的窗格操作、类 vim 的编辑区/命令行区分工都由这一层承载。

现有 `com.flora.os.shell.color`（ANSI 颜色/样式常量：`AnsiConsole`、`ShellColor`、`ShellStyle` 等，当前未导出未使用）可迁移进 `tui/` 作为颜色基元，顺带解决其"定义即闲置"的状态。

## 9. 场景映射

| 场景 | 组成 | 典型命令 | 特化点 |
|---|---|---|---|
| AI Agent | 指令组件（库形态） | `osmetes.check`、`ramet.gen` | 自动生成工具 schema 与 JSON 结果；需要机器可读返回时实现 `AgentView` |
| 普通 shell 指令 | `Entry` + 指令组件 | 现有三个模块的入口收编 | 默认即用 |
| 类 tmux | 组件 + `TuiComponent` | `session.new`、`pane.split`、`pane.kill` | `TuiView.bindKeys` 绑窗格快捷键，`Layout` 管窗格树 |
| 类 vim | 组件 + `TuiComponent` | `buffer.write`、`search.next` | 领域状态（当前文件/光标）由 TuiComponent 持有，模式切换由 `bindKeys` 表达 |
| 业务 GUI | 组件 + `GuiAdapter` | `gui --flavor javafx` | 业务实现自己的 UI 与事件循环 |
| 微信融合 | 组件 + `TuiComponent` + 微信渠道 | — | 微信渠道提交 `InputEvent`、挂 `OutputSink`（§14） |

所有场景**共享**：命令声明、参数解析、help 聚合、指令组件。**差异**：仅挂件的组合（Entry / TuiComponent / GuiAdapter / 渠道），与可选的命令特化层。

## 10. 扩展机制

新增命令：写一个 `Command` 类 + `component.register(...)`（或 SPI 声明，`registerBySpi()` 自动发现）。

新增接入方式：

1. 批量：`Entry` + 指令组件，开箱即用；
2. TUI：`TuiComponent`（注入指令组件），把其 `tui` 指令注册进外层组件；
3. GUI：实现 `GuiAdapter`（SPI 注册），框架经 `gui` 命令发现并调用；
4. 新渠道（如微信）：把输入归一化为 `InputEvent` 提交给组件，需要回写时挂 `OutputSink`。

## 11. 零依赖策略与已知难点

- **ANSI 渲染 / 屏幕缓冲 / 布局**：纯 JDK 可完成，无难点。
- **参数解析**：手写状态机即可，本项目入口现状已证明规模可控。
- **原始模式**（TUI 专属）：Unix 借 `stty` 子进程实现零依赖；Windows 经 FFM downcall `kernel32` 实现（见 §8）。
- **GUI / 指令 SPI**：JPMS 标准机制——业务模块声明 `provides`，框架模块声明 `uses`；`gui` 指令与 `GuiAdapter` 经 `ServiceLoader` 发现，`tui` 指令由用户代码显式注册。

## 12. 与现有代码的关系（收编路径）

- `OsmetesCli`、`Ramet`、`Tangle` 的入口改为"命令类 + 指令组件 + `Entry`"，手写参数循环替换为 `ArgSpec` 声明，帮助文本由声明生成，行为不变、代码量下降。
- 收编工具不注册 `tui` 指令，无参数调用的"打印用法退出"行为与现状一致；需要交互的工具（如 Hanako）显式组装 `TuiComponent`。
- `com.flora.os.shell.color` 迁入 `tui/` 复用。
- `flora-root/module-info.java` 需新增 `exports com.flora.shell` 及子包导出（若采用 §3 推荐方案）。

## 13. 决策点与开放问题

1. **放置位置**：flora-root（推荐）还是独立 flora-shell 模块——需用户确认。
2. **命令命名**：点分路径（推荐）与嵌套 `CommandGroup` 对象两种表达子命令树的方式，前者更贴合"心智模型简单"，后者层级能力更强。
3. **参数校验错误模型**：统一"结构化的参数错误"是否也用于批量入口的 stderr 展示格式，待定。
4. **输入归一化形态**：`InputEvent` 是否复用 `ParsedArgs` 构造路径（结构化调用直接进 `Invocation` 构造），待定。
5. **执行串行化**：组件内置单执行锁还是单线程事件循环，锁粒度与超时策略待定。
6. **GUI 多实现裁决**：多个 `GuiAdapter` 同名时按其自述 `priority()` 裁决（语义复用 `com.flora.common.register` 的裁决思想），`--flavor` 选择具体实现，细节待定。
7. **内置指令覆写规则**：用户命令覆写内置指令是否要显式声明（如实现某个标记接口）还是仅靠 `priority()`，待定。

## 14. 多渠道共享组件（微信 + TUI 融合场景）

**需求**：一个 Agent TUI 连接微信，微信发来的消息与本地键盘输入"汇总到同一个流"一起执行、一起显示——类比一个 tmux session 被两个 ssh 同时访问。

**结论**：框架积木原生支持，且**命令声明层零改动**。微信只是一个"渠道"：把消息归一化为 `InputEvent` 提交给与 TUI 共用的同一个 `CommandComponent`，并挂一个 `OutputSink` 回写微信；命令结果经组件扇出到屏幕 + 微信。

### 14.1 拓扑对应（tmux 多 client 模型）

| tmux | 框架 | 说明 |
|---|---|---|
| session | `CommandComponent` + TuiComponent 的领域状态 | 命令执行与状态——**唯一** |
| client (ssh) | 渠道（`InputEvent` 提交 + `OutputSink`） | 输入渠道 + 输出目的地——**可多个** |
| 共享输出缓冲 | `OutputMultiplexer` | 广播到所有已挂载 sink |

### 14.2 融入方式（三处都在框架边界，不碰命令）

1. **微信 = 一个渠道**：微信连接读消息 → 归一化 `InputEvent` → `component.submit(...)`；回写消息 → `attach(WeChatSink)`。命令代码对此完全无感知。
2. **共用组件**：键盘（经 TuiComponent）与微信都提交给同一个 `CommandComponent`，串行队列保证不抢状态。
3. **输出扇出**：`ScreenSink`（TUI 屏）与 `WeChatSink` 都挂在同一个 `OutputMultiplexer` 上，命令只管 `out().println(...)`，两边都收到。

### 14.3 需要补强的两块框架基础设施

- **输入归一化**：键盘产生 `KeyEvent`，微信消息是字符串，统一成 `InputEvent`（§7.2），否则各渠道各自解析会分叉。
- **执行串行化**：多个渠道同时提交会抢状态，`CommandComponent` 内置单执行队列，命令排队执行——这正是 tmux 多 client 共享 session 时的内在约束。

### 14.4 命令层零改动原则重申

这是关键收益：你写的 `Command`（`name()/args()/usage()/execute()`）完全不知道输入来自键盘还是微信。若某些指令需限制来源（如 `gui`/`exit` 不允许微信触发），在特化接口 `SourceRestricted` 声明渠道白名单，仍是"一处定义、按需特化"，不污染命令本体。

### 14.5 心智模型检查

本场景不需要新增任何概念：微信只是又一个渠道（`InputEvent` 提交 + `OutputSink`），与键盘、屏幕地位相同。组件的串行执行与输出扇出（§7）天然支持多渠道共享，§2 的心智模型原样适用。

以上为方案主体。评审通过后按此落地实现，实现同样保持零依赖。
