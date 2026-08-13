# CLI 命令框架设计方案

日期：2026-08-07
修订：2026-08-12（概念收敛：一期范围收敛为纯 CLI 命令框架；TUI/GUI/多渠道降级为未来增量；输出接口合并；InputEvent 归一化与领域状态契约澄清；模块归属改为独立 `flora-shell`）
状态：已实现（2026-08-12，一期 CLI 框架落地并收编三个模块入口）

## 1. 背景与目标

项目内 `flora-osmetes`、`flora-ramet`、`flora-tangle` 的命令行入口全部是手写位置参数解析 + `System.out` 打印，重复度高、无统一帮助、无法复用。同时 AI Agent 需要把工具以结构化方式暴露。本项目当前没有任何 CLI 参数解析框架或命令定义抽象。

本框架的目标：

- 一套命令定义，同时服务于 **批量调用（一次性执行）** 与 **AI Agent（结构化调用）**，并为将来的 **交互式 TUI**、**GUI（业务自持）**、**多渠道融合（如微信 + TUI）** 预留清晰的增量接入点；
- 命令类**自描述**：名称、参数、帮助都聚合在命令类内部，help 由聚合层统一生成，而非散落各处的字符串；
- 允许"一处定义共享"，也允许**按接入方式特化**（某个命令只给 Agent 定义返回 schema）；
- **零第三方依赖**；
- 心智模型尽量小：一个干净的指令组件（路由 + 执行 + 输出扇出），TUI、GUI、渠道都是可选的增量挂件。

**范围声明（本期）**：一期只实现**命令行命令框架**——`Command` + `CommandComponent` + `ArgSpec`/`ArgParser` + help 聚合，并收编三个模块的现有入口。TUI、GUI、微信渠道属于本方案描述的未来增量方向（§12 场景映射），但**不在本期交付范围**，其接口仅预留。

## 2. 心智模型

> 一个命令 = 一个类。命令类自己说出"我叫什么、要什么参数、做什么、帮助是什么"。
> 一个指令组件 = 干净的路由与执行：注册命令、串行分派、输出扇出。它无状态、无 UI、不拥有输入源、不管生命周期。
> TUI、微信、GUI 都是可选的"增量挂件"——它们把输入变成调用提交给组件，从组件拿到输出。

核心概念：

1. **Command（命令）**——自描述的执行单元，一个类一个命令。
2. **CommandComponent（指令组件）**——注册、串行分派、输出扇出；零状态，可独立工作（批量、Agent 只用它）。

以及三个"挂件"概念（非本期交付，但构成完整心智模型，作为未来增量）：

- **TuiComponent（TUI 组件）**——交互界面：键盘输入源、光标/窗格/布局、屏幕渲染；构造时注入一个指令组件负责执行命令。
- **渠道（Channel）**——任何能产生"一次调用"的东西（键盘、微信长连接、argv）。渠道把输入归一化为 `InputEvent` 提交给组件，需要回写时挂自己的 `OutputSink`。
- **OutputSink（输出汇）**——屏幕、微信连接、stdout……组件通过 `Output` 把执行结果扇出到所有已挂载的输出汇。

一次输入到一次执行的完整管线：

```
渠道（键盘 / 微信 / argv）
  → InputEvent（来源 + 命令调用描述）
  → CommandComponent.submit（串行队列）
  → 分词 + 查找命令 + ArgParser 校验（→ ParsedArgs）
  → Invocation（命令 + ParsedArgs + Output + 来源）
  → execute → CommandResult
  → Output 扇出到所有已挂载的 OutputSink
```

书写一个新命令的默认路径只有四步（回答四个问题）：

```
叫什么（name） → 做什么（description） → 收什么参数（args） → 收到后干嘛（execute）
```

四个问题全部写在同一个类里。做到这四步，批量、Agent、GUI、TUI、微信融合全部可用；TUI 提供通用的参数表单渲染。只有需要**专属交互**（vim 的按键模式、tmux 的窗格操作）时，命令才额外实现特化接口——这是可选的增量，不是默认负担。

### 2.1 接入方式

| 接入方式 | 组成 | 生命周期 | 交付阶段 |
|---|---|---|---|
| 批量 | 指令组件 + `InputEvent.ofCliArgs` | 一次性（argv → 执行 → 退出码） | **本期** |
| Agent | 指令组件（库形态，结构化调用） | 按会话多次调用 | **本期** |
| TUI | 指令组件 + `TuiComponent`（`tui` 指令） | 进入交互循环，`exit` 结束 | 未来增量 |
| GUI | 指令组件 + `GuiAdapter` | 业务自持 | 未来增量 |
| 微信融合 | 指令组件 + `TuiComponent` + 微信渠道 | 同 TUI | 未来增量 |

框架不提供驻留逻辑，也没有专门的入口壳：命令行工具把 argv 经 `InputEvent.ofCliArgs` 切成命令名 + 参数直接提交给指令组件，取返回的退出码结束进程。本期框架不实现 TUI/GUI，进入交互属于未来增量（§12）；未注册任何交互指令的工具无参数调用默认报错（提示输入 `help`、非零退出码）。

## 3. 模块与包结构

**放置位置（本期决策）**：独立模块 **`flora-shell`**，顶层包 `com.flora.shell`，依赖 `flora-root`（零依赖工具库）。理由见 §13.1：flora-root 已承载多域能力、规模可观，不应再堆入 TUI 这类高度专门化的领域；`flora-shell` 单独演化、按需被工具模块依赖。

```
com.flora.shell
├── Command                  # 命令接口（声明 + 执行 + 特化入口）
├── CommandService           # 指令组件：注册、别名、串行分派、输出扇出（零状态、无 UI）
├── Dispatcher               # 分派门面：命令执行中转发重入分派的入口
├── Invocation               # 调用上下文：命令 + 参数 + Output + 来源 + 转发入口
├── InputEvent               # 归一化输入（来源 + 命令调用描述：argv / 结构化 / cliArgs）
├── CommandResult            # 执行结果（退出码 / 结构化数据）
├── builtin/                 # 内置指令（预制）：help / alias / gui
├── spec/
│   ├── ArgSpec              # 参数/选项声明（声明式，非解析代码）
│   ├── ParsedArgs           # 解析结果
│   └── ArgParser            # 零依赖解析器（cli 串 / 列表 / JSON 输入）
├── help/
│   ├── HelpProvider         # 命令向 help 聚合层提供数据
│   └── HelpRenderer         # 渲染成文本树 / Agent 工具描述
└── output/
    ├── OutputSink           # 输出汇接口（唯一）
    └── Output               # 命令写输出用的门面（扇出到所有 sink）
```

`tui/`（TuiComponent、KeyInputSource、ScreenSink、RawTerminal、KeyEvent、ScreenBuffer、Layout）与 `GuiAdapter` 属于未来增量，本期不建包；§12 给出它们的预期落点。

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
- `execute()` 不直接碰 `System.out`，而是通过 `Invocation` 里的 `Output` 写输出，因此同一份实现可同时跑在批量打印与未来 TUI 面板、微信回写上。
- `priority()` 只用于**同名冲突裁决**：内置指令（help/gui）以低优先级注册，用户定义同名命令可覆写内置指令；用户命令之间同名冲突直接抛异常（视为 bug），不裁决。
- **命令应无状态**：领域状态由业务代码通过 `ScopedValue` 在调用前绑定，命令在 `execute` 内读取，不存于命令或框架内。保证同一命令在多个组件中重复注册（各 `registerBySpi()`）时行为一致。

### 4.1 内置指令（预制）

| 指令 | 提供方 | 行为 | 阶段 |
|---|---|---|---|
| `help [cmd]` | 组件 | 遍历注册表，按点分树渲染帮助（自举） | 本期 |
| `alias <name> <cmd> [args...]` | 组件 | 注册别名，之后该名字被调用时转发到目标命令 | 本期 |
| `gui [--flavor name]` | 组件 | 经 SPI 发现 `GuiAdapter`，调用其 `launch(ctx)` | 未来增量 |
| `tui` | TuiComponent | 进入交互循环：`TuiComponent(inner).run()`，`exit` 结束 | 未来增量 |
| `exit` | TuiComponent | 结束交互循环 | 未来增量 |

本期实现 `help` 与 `alias`。`tui`/`exit`/`gui` 属于未来增量，注册机制随 `TuiComponent`/`GuiAdapter` 一起设计（§12），不阻塞本期。本期无交互指令的工具无参数调用默认报错（提示输入 help、非零退出码）。

**框架不提供默认的 `--help` 拦截**：`--help` 不再被组件特殊处理，而是作为一个普通参数走各命令的 `ArgSpec` 解析；需要 `--help` 的工具可自行声明 `--help` 选项，或基于转发底座（见 §7）把 `--help` 转发到 `help` 命令。`alias` 即为转发底座的一个落地示例。

### 4.2 接入方式特化（可选增量）

命令类可以额外实现以下接口，按需提供专属行为：

```
interface CliView {      // 批量入口专属：argv 级前置校验 / 定制错误输出，默认由框架提供
    default String beforeExecute(List<String> rawArgs) { ... }  // 返回 null 或错误
}
interface TuiView {      // TUI 专属：按键绑定、会话内视图（未来增量）
    default void bindKeys(KeyMap km) { }
    default View view(TuiComponent tui) { return null; }   // null = 用通用表单
}
interface AgentView {    // Agent 专属：定制工具描述 / 返回值 schema，默认由声明自动生成
    default ToolSchema toolSchema() { ... }
}
interface SourceRestricted { // 来源限制：声明允许触发本命令的渠道白名单（如 gui/exit 不允许微信触发）
    default Set<ChannelId> allowedSources() { return Set.of(); }  // 空 = 不限制
}
```

心智模型规则：**"不实现任何特化接口"= 各接入方式通用；"实现某个特化接口"= 只对该方式生效，其他方式仍走默认。** 特化只针对行为，不复制声明——声明永远只有 `Command` 接口那一份。

`SourceRestricted` 的渠道身份用 `ChannelId` 类型（而非裸字符串），配合 `com.flora.common.register` 的注册裁决思想提供类型安全的渠道注册与来源过滤（§4.3）。本期无渠道，`ChannelId` 预留。

## 5. 参数声明与解析

`ArgSpec` 声明式描述参数，覆盖所有输入方式（argv、未来 TUI 命令面板、Agent JSON）：

- **选项**：`--name` / `-n`（可带值、可重复、可 bool 开关）
- **位置参数**：`<src>`（可选/必选/变长）
- **值约束**：类型（int/str/enum/list）、默认值、合法枚举、数值范围
- **组合规则**：互斥、必选其一（由解析器校验）

解析器只有一个入口，`ParsedArgs parse(List<String> argv)`；AI Agent 输入（JSON 对象）先归一化成同一种 `Map<String,Object>` 再走同一校验，从而 **CLI 的 `--port 8080` 与 Agent 的 `{"port":8080}` 落到同一套校验逻辑**。

解析失败时返回结构化的错误（缺哪个参数、哪个值非法、期望什么），由各接入方式按自己的方式呈现：批量调用打印到 stderr 并置非零退出码，未来 TUI 画到命令面板，Agent 回传错误对象。

## 6. Help 聚合到命令类内部

设计原则：**命令类是 help 的唯一事实来源**。`Command` 的声明层（name/description/args/usage）就是 help 数据，不需要单独维护一份帮助字符串。

- 单命令 help：由命令类声明 + 示例段自动渲染。
- 全局 help：组件遍历所有命令，按点分名构建命令树，`HelpRenderer` 渲染：
  - **批量**：`help`（全局树）或 `help <cmd>`（单命令）→ 文本树打印；框架不默认拦截 `--help`，工具可自行声明 `--help` 选项或经转发底座（§7）把 `--help` 映射到 `help` 命令；
  - **Agent**：会话初始化时宿主直接把整棵命令树转成工具列表描述，随 `toolSchema` 交给模型。
  - （未来 TUI：帮助页以分屏/滚动视图展示。）
- 聚合顺序与层级由组件保证，命令之间无感知，新增命令自动出现在全局 help 中，无需改动聚合代码。

## 7. 指令组件、渠道与执行管线

### 7.1 CommandComponent（指令组件）

指令组件是**干净的路由与执行单元**，可独立工作（批量、Agent 只用它）：

- **注册**：`register(Command)` / `registerBySpi()`；
- **分派**：`submit(InputEvent)`——串行队列执行（多渠道并发提交安全），查找命令 + `ArgParser` 校验 → 构造 `Invocation` → `execute`；
- **别名与转发**：未命中真实命令时按别名转发；命令可经 `Invocation.forward` 重入分派（§7.3）；
- **输出扇出**：`attach(OutputSink)`，执行结果经 `Output` 广播；
- **零状态、无 UI、不拥有输入源、不管生命周期**。

### 7.2 执行管线

```
渠道（键盘 / 微信 / argv）
  → InputEvent（来源 + 命令调用描述）
  → CommandComponent.submit（串行队列）
  → 分词 + 查找命令 + ArgParser 校验（→ ParsedArgs）
  → Invocation（命令 + ParsedArgs + Output + 来源）
  → execute → CommandResult
  → Output 扇出到所有已挂载的 OutputSink
```

**`InputEvent` 的归一化形态（本期契约）**：`InputEvent` 承载两部分——`来源`（`ChannelId`，本期为 argv/Agent 固定枚举）+ `命令调用描述`。命令调用描述只有两种既定形态：

1. **argv 序列**（`List<String>`）：argv、未来 TUI 命令面板的文本先分词成 argv，再走 `ArgParser.parse(argv)`；
2. **结构化参数**（`Map<String,Object>`）：快捷键绑定、Agent JSON 归一化成同一种 Map，直接走声明校验（`ArgParser.validate(map)`）。

两种形态由 `InputEvent` 的 `describeArgs()` 统一暴露，组件内部转换成 `ParsedArgs` 后进入 `Invocation`。归一化在**渠道边界**完成——各渠道只负责把原生输入切成 argv 或 Map 之一，组件只接收 `InputEvent`，不再二次猜测。这保证多渠道不会在组件内部分叉。

### 7.3 转发底座（指令间相互转发）

框架提供一个**指令间相互转发的通用原语**，使 `alias`、`--help` 映射、子命令分发等能力都建立在"一个命令把请求转给另一个命令"之上，而非硬编码在入口或框架里。

- **`Dispatcher`**：`CommandService` 实现的分派门面（只暴露 `submit`），`Invocation` 携带它，命令在 `execute` 内通过 `ctx.forward(target, argv)` 转发——命令只依赖 `Dispatcher` 接口，不依赖具体 `CommandService`，避免循环依赖。
- **转发重入完整管线**：转发会重建 `InputEvent`（沿用当前来源渠道）再走 `submit`，因此目标命令照常经过参数解析、来源限制、输出扇出，而非直接调 `execute()`。
- **别名（alias）**：`alias <name> <cmd> [args...]` 注册一个名字到"目标命令 + 前缀参数"的映射；分派未命中真实命令时，按别名把"前缀参数 + 本次参数"转发给目标。`alias` 是转发底座的一个内置落地示例。
- **递归保护**：转发 / 别名解析共用同一分派管线与深度上限（`MAX_FORWARD_DEPTH`），超过即视为存在别名环并拒绝，防止无限递归。
- **`--help` 不默认提供**：框架不再拦截 `--help`。工具需要 `--help` 时，可用转发把 `--help` 映射到 `help` 命令，或自行声明 `--help` 选项——都由工具在转发底座之上自行构建。

### 7.4 输出扇出（单一输出接口）

```
interface OutputSink { void emit(String text); void emitError(String text); }
interface Output { void print(String s); void println(String s); void error(String s); }
// 框架提供扇出实现：实现 Output，把每次调用 fan-out 到所有已挂载的 OutputSink
class OutputMultiplexer implements Output {
    void attach(OutputSink s);   // 未来 TUI 挂 ScreenSink，微信挂 WeChatSink
}
```

**本期契约（合并双接口）**：命令写输出**只有 `Output` 一个门面**，`Invocation.out()` 返回的就是框架的扇出实现。`OutputSink` 是业务输出汇实现的最小接口：`emit(text)` 对应 `print`/`println`（`println` 由扇出层补换行），`emitError` 对应 `error`。二者是"一个接口，两个视角"（调用方看 `Output`，输出汇实现 `OutputSink`），不再各带一套语义。本期批量场景无挂载 sink 时，扇出实现退化直达 stdout/stderr。

### 7.5 状态与上下文

组件不持有任何状态。状态按归属分家：

- **UI 状态**（光标、窗格、布局、当前视图）——归 `TuiComponent`（未来增量）；
- **领域状态**（当前文件、buffer、打开的会话）——归调用方（TUI/用户代码）持有；
- **领域状态经 `ScopedValue` 传递**：框架不承载、不透传状态。业务代码在调用前用 `ScopedValue` 绑定领域对象，命令在 `execute` 内用 `ScopedValue.get(...)` 读取。`Invocation` 只含 command/args/out/source，不含状态。

**领域状态访问契约（本期澄清）**：本期批量/Agent 场景是单次调用，命令在自身执行线程内 `ScopedValue.get(...)` 即读到绑定值，无并发。对未来多渠道共享场景（微信+键盘同时访问领域状态），由各渠道在各自的执行作用域绑定领域对象；框架**不承诺跨渠道并发下的线程安全**——串行队列（§7.1）保证命令间不并发抢占，但同一领域对象若跨调用共享，状态一致性由宿主通过不可变状态或加锁自行保证。本期不引入领域状态的锁/快照机制，留到多渠道场景落地时再定（§13）。

### 7.6 命令行入口（`InputEvent.ofCliArgs`）

- 工具 `main` 把 argv 经 `InputEvent.ofCliArgs(cliArgs)` 切成命令名 + 参数，`commandService.submit(event)` 执行并取返回的退出码退出。框架不提供驻留逻辑，也没有专门的入口壳。
- argv 为空（无命令名）：`ofCliArgs` 抛 `IllegalArgumentException`，由工具自行判断并报错（与现有工具"无参数打印用法退出"的行为一致）。需要"无参数进入交互"的工具由宿主自行决定（未来增量）。框架不默认渲染全局帮助或拦截 `--help`（见 §7.3）。
- GUI：`GuiAdapter.launch(ctx)` 内运行业务自己的事件循环（未来增量，§12）。

## 8. （未来增量）TUI 组件与渲染原语

> 本节为未来 TUI 增量的**预期设计方向**，本期不实现。据此 `flora-shell` 本期不建 `tui/` 包。

### 8.1 TuiComponent

TUI 组件 = 交互界面的所有者，构造时注入一个 `CommandComponent` 负责执行命令：

- **持有**：键盘输入源（`KeyInputSource`）、光标/窗格/布局状态、屏幕输出汇（`ScreenSink`）、领域状态；
- **循环**：读取键盘/其他输入 → 构造 `InputEvent`（带上领域状态）→ `component.submit(...)` → 结果渲染到屏幕；
- **提供指令**：`tui` 与 `exit`，随 `TuiComponent` 作为整体一次注册进外层组件（如 `component.attach(tuiComponent)`），由 TUI 内部自管理这两个指令，而非把指令单独注册到内外不同组件。

### 8.2 渲染原语

- **RawTerminal**：Unix 用 `ProcessBuilder` 调 `stty raw -echo`（进入）与 `stty sane`（恢复），进程退出钩子保证恢复；**Windows 经 FFM 实现零依赖全支持**——`flora-root` 的 `com.flora.os.natives.ffm.NativeLib` 用 JDK 标准 FFM 直接 downcall `kernel32` 的 `GetStdHandle`/`GetConsoleMode`/`SetConsoleMode` 切换 raw mode，无需 JNA/JNI。使用前提：启动 JVM 需 `--enable-native-access=com.flora.root`。注意经典 Conhost 与 Windows Terminal 的 ConPTY 在 raw 语义上略有差异；该方案覆盖经典控制台场景。
- **KeyEvent**：从原始字节流解析按键（普通键、方向键、Ctrl 组合、Fn），跨终端映射到统一键名，供 `KeyMap` 绑定。
- **ScreenBuffer**：离屏字符缓冲 + 全量 diff，每次渲染只输出变化区域，避免整屏闪烁。
- **Layout**：窗格树（split 上下/左右）、状态栏、命令输入面板。类 tmux 的窗格操作、类 vim 的编辑区/命令行区分工都由这一层承载。

现有 `com.flora.os.shell.color`（ANSI 颜色/样式常量：`AnsiConsole`、`ShellColor`、`ShellStyle` 等，**已在 flora-root 导出**）届时可迁移进 `tui/` 作为颜色基元。

## 9. （未来增量）GuiAdapter 与指令 SPI

> 本期不实现，接口预留。

`gui` 指令与 `GuiAdapter` 经 JPMS `ServiceLoader` 发现（业务模块 `provides`，框架模块 `uses`）。多个 `GuiAdapter` 同名时按其自述 `priority()` 裁决（语义复用 `com.flora.common.register` 的裁决思想），`--flavor` 选择具体实现。`GuiAdapter.launch(ctx)` 内运行业务自己的事件循环，把 GUI 事件喂给组件、从组件收输出；GUI 结束时告知框架（`ctx.finish(code)`），进程随之退出。

## 10. 扩展机制（本期范围）

新增命令：写一个 `Command` 类 + `component.register(...)`（或 SPI 声明，`registerBySpi()` 自动发现）。

本期新增接入方式：批量（`InputEvent.ofCliArgs` + 指令组件）与 Agent（库形态）开箱即用。TUI / GUI / 新渠道属未来增量，接入点见 §12 场景映射。

## 11. 零依赖策略与已知难点

- **参数解析 / help 聚合 / 输出扇出**：纯 JDK 可完成，无难点；本项目入口现状已证明规模可控。
- **原始模式 / GUI / 指令 SPI**（未来增量）：Unix 借 `stty` 子进程、Windows 经 FFM downcall `kernel32`、JPMS `ServiceLoader`——路径已在 §8/§9 描述，本期不阻塞。

## 12. 与现有代码的关系（收编路径）

- `OsmetesCli`、`Ramet`、`Tangle` 的入口改为"命令类 + 指令组件 + `InputEvent.ofCliArgs`"，手写参数循环替换为 `ArgSpec` 声明，帮助文本由声明生成，行为不变、代码量下降。
- 收编工具不注册交互指令，无参数调用的"打印用法退出"行为与现状一致。
- 未来 TUI 增量：需要交互的工具（如 `cultivating/flora-hanako`，当前为 Web/Javalin 方案、无 CLI/TUI 入口）届时显式组装 `TuiComponent`。
- `com.flora.os.shell.color` 已在 flora-root 导出，未来 TUI 迁入 `tui/` 复用（本期不动）。

**场景映射（未来增量预览）**：

| 场景 | 组成 | 典型命令 | 特化点 |
|---|---|---|---|
| AI Agent | 指令组件（库形态） | `osmetes.check`、`ramet.gen` | 自动生成工具 schema 与 JSON 结果；需要机器可读返回时实现 `AgentView` |
| 普通 shell 指令 | `InputEvent.ofCliArgs` + 指令组件 | 现有三个模块的入口收编 | 默认即用（**本期**） |
| 类 tmux | 组件 + `TuiComponent` | `session.new`、`pane.split`、`pane.kill` | `TuiView.bindKeys` 绑窗格快捷键，`Layout` 管窗格树 |
| 类 vim | 组件 + `TuiComponent` | `buffer.write`、`search.next` | 领域状态（当前文件/光标）由 TuiComponent 持有，模式切换由 `bindKeys` 表达 |
| 业务 GUI | 组件 + `GuiAdapter` | `gui --flavor javafx` | 业务实现自己的 UI 与事件循环 |
| 多渠道融合 | 组件 + `TuiComponent` + 渠道 | — | 渠道提交 `InputEvent`、挂 `OutputSink` |

所有场景**共享**：命令声明、参数解析、help 聚合、指令组件。**差异**：仅挂件的组合（`InputEvent.ofCliArgs` / TuiComponent / GuiAdapter / 渠道），与可选的命令特化层。

## 13. 决策点与开放问题

1. **放置位置（已决策）**：独立 **`flora-shell`** 模块，依赖 flora-root。flora-root 已承载约 55 个导出包、数百个类，TUI 等高专门化领域不应再堆入；独立模块单独演化、按需被依赖。若后续确认根因是过度设计可回落，但本期采用独立模块。
2. **命令命名**：点分路径（推荐）与嵌套 `CommandGroup` 对象两种表达子命令树的方式，前者更贴合"心智模型简单"，后者层级能力更强。
3. **参数校验错误模型**：统一"结构化的参数错误"是否也用于批量入口的 stderr 展示格式，待定。
4. **`InputEvent` 两形态落地**：argv 序列与结构化 Map 的统一暴露形态（`describeArgs()`）细节待实现时定。
5. **执行串行化**：组件内置单执行锁还是单线程事件循环，锁粒度与超时策略待定（本期单渠道场景无并发压力，可先取最简单实现）。
6. **GUI 多实现裁决（未来增量）**：多个 `GuiAdapter` 同名时按 `priority()` 裁决，`--flavor` 选具体实现，细节待未来实现时定。
7. **内置指令覆写规则**：用户命令覆写内置指令是否要显式声明（如实现某个标记接口）还是仅靠 `priority()`，待定。
8. **领域状态并发（未来增量）**：多渠道共享时领域对象的线程安全策略（不可变 / 锁 / 快照），留待多渠道场景落地时定，本期不引入。

## 14. （未来增量）多渠道共享组件（微信 + TUI 融合场景）

> 本期不实现，记录需求与框架如何原生支持，命令声明层零改动。

**需求**：一个 Agent TUI 连接微信，微信发来的消息与本地键盘输入"汇总到同一个流"一起执行、一起显示——类比一个 tmux session 被两个 ssh 同时访问。

**框架支持方式**：微信只是一个"渠道"：把消息归一化为 `InputEvent` 提交给与 TUI 共用的同一个 `CommandComponent`，并挂一个 `OutputSink` 回写微信；命令结果经组件扇出到屏幕 + 微信。命令代码对此完全无感知。需要的两块框架基础设施——输入归一化（§7.2 已定契约）与执行串行化（§7.1 已定）——均在本期框架内，无需新增概念。若某些指令需限制来源（如 `gui`/`exit` 不允许微信触发），用特化接口 `SourceRestricted` 声明渠道白名单（§4.2）。

以上为方案主体。一期按 §3/§10 实现纯 CLI 命令框架并收编三个模块入口，保持零依赖；TUI/GUI/多渠道按 §8/§9/§14 作为未来增量演进。
