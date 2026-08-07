# 用 FFM 实现 Windows 控制台原始模式（raw mode）

日期：2026-08-07
状态：技术探索（可落地实现笔记）
关联：CLI/TUI 框架设计文档 `idea20260807-cli-tui-command-framework.md` §8 / §11
依赖：`flora-root` 的 `com.flora.os.natives.ffm.NativeLib`（从历史 `390bd7f` 恢复，见提交 `8461095`；返回布局修复见 `1b9129b`）

## 1. 背景：为什么 Windows 的 raw mode 必须走原生调用

TUI 要接管整块屏幕、即时响应每个按键，必须把终端切成**原始模式（raw mode）**：关掉内核行规则的行缓冲、回显、Ctrl+C 特殊处理，让程序直接拿到原始按键流。

- Unix 上可用外部命令 `stty raw -echo` 借道，零依赖即可；
- Windows 控制台没有 `stty`，必须调 Win32 控制台 API `SetConsoleMode` 关掉 `ENABLE_LINE_INPUT` / `ENABLE_ECHO_INPUT` / `ENABLE_PROCESSED_INPUT`。

`SetConsoleMode` 是 `kernel32.dll` 的导出函数，Java 标准库不封装，需用 **FFM（Foreign Function & Memory，JDK 标准 API）** 直接 downcall——既做到零第三方依赖（无需 JNA/JNI），又契合项目"零依赖"硬约束。

## 2. 涉及的 Windows 控制台 API

| 函数（kernel32） | 签名（C） | 作用 |
|---|---|---|
| `GetStdHandle` | `HANDLE GetStdHandle(DWORD nStdHandle)` | 取标准输入/输出/错误的句柄 |
| `GetConsoleMode` | `BOOL GetConsoleMode(HANDLE h, LPDWORD lpMode)` | 读当前控制台模式到 4 字节缓冲 |
| `SetConsoleMode` | `BOOL SetConsoleMode(HANDLE h, DWORD dwMode)` | 设置控制台模式 |

标准句柄常量（传给 `GetStdHandle`）：

- `STD_INPUT_HANDLE  = -10`
- `STD_OUTPUT_HANDLE = -11`

输入模式标志（`GetConsoleMode`/`SetConsoleMode` 的 `dwMode`，位或）：

- `ENABLE_PROCESSED_INPUT   = 0x0001` —— 处理 Ctrl+C 等（raw 时关）
- `ENABLE_LINE_INPUT        = 0x0002` —— 行缓冲（raw 时关）
- `ENABLE_ECHO_INPUT        = 0x0004` —— 回显（raw 时关）
- `ENABLE_WINDOW_INPUT      = 0x0008`
- `ENABLE_MOUSE_INPUT       = 0x0010`
- `ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200`

输出模式标志：

- `ENABLE_PROCESSED_OUTPUT        = 0x0001`
- `ENABLE_WRAP_AT_EOL_OUTPUT      = 0x0002`
- `ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004` —— 开启后支持 ANSI 转义（颜色/光标移动）

raw mode 的关键是：对输入句柄清掉 `0x0001 | 0x0002 | 0x0004`。

## 3. 用 Native 统一门面调用（核心片段）

`Native` 是统一入口：以库名为键缓存已加载的 `NativeLib`，调用方**无需先 load**。
`callPtr` 调返回指针的函数，`callVoid` 调无返回值函数；参数 `Integer` 映射 `int`(32 位 DWORD)，`MemorySegment` 映射指针。

```java
import com.flora.os.natives.ffm.Native;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

// 标准句柄
static final int STD_INPUT_HANDLE  = -10;
static final int STD_OUTPUT_HANDLE = -11;
// 输入模式标志
static final int ENABLE_PROCESSED_INPUT = 0x0001;
static final int ENABLE_LINE_INPUT      = 0x0002;
static final int ENABLE_ECHO_INPUT      = 0x0004;

try (Arena a = Arena.ofConfined()) {

    // 1) 取句柄（返回 64 位 HANDLE，必须用 callPtr 拿 MemorySegment；库句柄由 Native 内部缓存）
    MemorySegment hIn  = Native.callPtr("kernel32", "GetStdHandle", STD_INPUT_HANDLE);
    MemorySegment hOut = Native.callPtr("kernel32", "GetStdHandle", STD_OUTPUT_HANDLE);

    // 2) 读当前输入模式（GetConsoleMode 的 out 参数：4 字节 DWORD 缓冲）
    MemorySegment inMode = a.allocate(4);
    Native.callVoid("kernel32", "GetConsoleMode", hIn, inMode);
    int current = inMode.get(ValueLayout.JAVA_INT, 0);

    // 3) 关掉行缓冲 / 回显 / 特殊处理 → raw
    int raw = current & ~ENABLE_LINE_INPUT & ~ENABLE_ECHO_INPUT & ~ENABLE_PROCESSED_INPUT;
    Native.callVoid("kernel32", "SetConsoleMode", hIn, raw);

    // 4) （可选）输出开启 ANSI 虚拟终端处理，支持颜色与光标移动
    MemorySegment outMode = a.allocate(4);
    Native.callVoid("kernel32", "GetConsoleMode", hOut, outMode);
    int o = outMode.get(ValueLayout.JAVA_INT, 0) | 0x0004; // ENABLE_VIRTUAL_TERMINAL_PROCESSING
    Native.callVoid("kernel32", "SetConsoleMode", hOut, o);
}
```

要点：

- `GetStdHandle` 返回 **64 位 HANDLE**，必须用 `callPtr`（返回 `MemorySegment`）。旧版 `NativeLib` 曾把返回布局写死成 `JAVA_INT`，会把高 32 位截断、拿到错误句柄；修复后 `descriptor` 按返回类型生成布局，才能正确取到句柄（见 `1b9129b`）。
- `GetConsoleMode` 的第二参是 `LPDWORD`（指针），在 arena 里 `allocate(4)` 一段内存传入，`callVoid` 后从偏移 0 读回 `int`。
- `Arena` 管理参数内存生命周期：字符串/缓冲用的 arena 必须活过 native 调用。
- `Native` 内部用 `Caches.memory()`（无界缓存）保存各库句柄，进程生命周期内常驻，无需手动 `load`/关闭。

## 4. 完整可复用封装（示意）

生产用法应保存原始模式并在退出时还原（否则程序崩了终端仍 raw，用户键盘无回显）。下面给出一个 `enable()` / `disable()` 骨架：

```java
public final class WindowsConsoleRaw implements AutoCloseable {
    private final MemorySegment hIn;
    private int savedInMode;
    private boolean applied;

    public WindowsConsoleRaw() {
        this.hIn = Native.callPtr("kernel32", "GetStdHandle", -10);
    }

    public void enable() {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment m = a.allocate(4);
            Native.callVoid("kernel32", "GetConsoleMode", hIn, m);
            savedInMode = m.get(ValueLayout.JAVA_INT, 0);
            int raw = savedInMode & ~0x0002 & ~0x0004 & ~0x0001;
            Native.callVoid("kernel32", "SetConsoleMode", hIn, raw);
            applied = true;
        }
    }

    public void disable() {
        if (!applied) return;
        Native.callVoid("kernel32", "SetConsoleMode", hIn, savedInMode); // 还原原始模式
        applied = false;
    }

    /** 注册 JVM 关闭钩子，退出前自动还原。 */
    public void installShutdownRestore() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::disable));
    }

    @Override public void close() {
        disable();
    }
}
```

调用方：

```java
try (WindowsConsoleRaw raw = new WindowsConsoleRaw()) {
    raw.enable();
    raw.installShutdownRestore();
    // ... TUI 事件循环：直接读按键字节流、自己画屏 ...
}
```

## 5. 使用前提与注意

- **native access 授权**：FFM 受限方法（含 `MemorySegment.reinterpret`、以及 `Linker.nativeLinker` 生成的 downcall 桩）需在启动 JVM 加 `--enable-native-access=com.flora.root`。`flora-root` 自身测试的 surefire 已配 `ALL-UNNAMED`；消费模块在自己的启动参数里加该 flag，否则 Java 24+ 仅警告、未来版本直接拦截。
- **模块依赖**：消费模块 `requires com.flora.root;`，`com.flora.os.natives.ffm` 已 `exports`。
- **经典控制台 vs ConPTY**：上述 `SetConsoleMode` 路径对经典 Conhost 直接有效；Windows Terminal 走 ConPTY（伪终端），输入是字节流、由 PTY 而非 console mode 控制，raw 的语义略有不同，但 `SetConsoleMode` 仍可用于经典控制台场景。跨终端一致性取决于前端的终端探测逻辑。
- **仅 Windows 调用**：`kernel32` 不存在于 Unix/macOS，这段代码应包在 `OS.name` 判断或 `Platform` 分支后，Unix 侧继续用 `stty`。

## 6. 与 CLI/TUI 框架的关系

该实现是设计文档 §8 `RawTerminal` 的 Windows 落地方式：把 `RawTerminal` 在 Windows 上的"进入/恢复原始模式"委托给上述 `WindowsConsoleRaw`，Unix 上仍用 `stty`，`KeyEvent` 解析层不变。由此设计文档 §11 所谓"Windows 无公开 Java API、只能用 PowerShell 退路"的局限被消除——FFM 提供零依赖的 Win32 调用，Windows 的 raw mode 可做到与 Unix 同等支持。

## 7. 参考提交

- `8461095` —— 从 `390bd7f` 恢复 `com.flora.os.natives.ffm` 包（含 `NativeLib`）。
- `1b9129b` —— 修复 `NativeLib.descriptor` 返回布局硬编码，使 `GetStdHandle` 等 64 位返回正确。
- `8128be0` —— CLI/TUI 设计文档，其中 §14 讨论多前端共享 Session（微信 + TUI 融合）。
