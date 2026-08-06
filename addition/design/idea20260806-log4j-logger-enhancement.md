# 日志包 log4j 风格对齐与惰性求值增强

日期：2026-08-06
模块：`flora-root`（`com.flora.runtime.log`）
智能体：CodeBuddy Code

## 1. 背景与目标

`flora-root` 自带零依赖日志包，此前仅支持 TRACE/DEBUG/INFO/WARN/ERROR 五级，
转换符为 `%d %t %p %c %m %mdc %n %%`。为向团队熟悉的 log4j2 习惯靠拢，并补齐
生产可用的能力，本次增强补齐以下特性：

1. **FATAL 级别**：高于 ERROR 的不可恢复故障级别。
2. **异常关联**：日志调用可携带 `Throwable`，并能通过 `Layout` 的 `%ex`/`%throwable`
   输出完整堆栈（与 SLF4J/log4j2 行为一致）。
3. **调用位置符**：`%C`（类）`%M`（方法）`%L`（行号）`%F`（文件），用于定位日志发生点。
4. **相对时间 `%r`**：自 JVM 启动到本次日志的毫秒数。
5. **高亮 `%highlight`**：按级别包裹 ANSI 颜色，适用于控制台。
6. **log4j 风格滚动归档 `filePattern`**：基于大小滚动时按 `%i` 序号、基于时间滚动时
   按 `%d{格式}` 生成归档文件名。
7. **惰性求值 `Supplier<String>` 重载**：级别未启用时跳过消息构造。

所有增强均建立在既有的 core / spi / impl 三层架构之上，**未改变日志系统的核心数据流**：
`Logger` → `LoggerImpl.log(...)` → `LogEvent` → `Appender.append(LogEvent)` → `Layout.format`。

## 2. 架构与契约

分层保持不动：

- `core`：`com.flora.runtime.log`（`Logger` / `LoggerFactory` / `Level` / `MDC` / `LogConfig`）
- `spi`：`com.flora.runtime.log.spi`（`Appender` / `Layout` / `LogEvent` / `RollingPolicy`）
- `impl`：`com.flora.runtime.log.impl`（`LoggerImpl` / `MessageFormatter` / `ConsoleAppender` /
  `FileAppender` / `RollingFileAppender`）

核心改动点沿既有契约扩展：

- `LogEvent` 新增两个字段：`Throwable throwable` 与 `StackTraceElement callerLocation`，
  原 5 参构造器委托给新的 7 参构造器（旧字段补 `null`），新增 `getThrowable()` /
  `getCallerLocation()`。旧调用方无需改动。
- `Layout` 注册新的转换器映射，并新增 `requiresCallerLocation()` 标志，供 `LoggerImpl`
  判断是否需要付出栈帧捕获的代价。
- `RollingFileAppender` 新增 `filePattern` 字段与对应的流式 `filePattern(...)` API，
  归档路径一律使用 `basePath.getFileSystem().getPath(...)` 构造，保证文件系统无关。

## 3. 关键设计决策

### 3.1 FATAL 级别

`Level` 新增 `FATAL(5)`，Javadoc 明确「FATAL 表示不可恢复的严重故障，高于 ERROR，
是允许输出的最高级别」。`isEnabled` 逻辑（`intValue <= other.intValue`）不变，
因此 FATAL 自然成为最高启用阈值。

### 3.2 异常关联

两种入口，均映射到同一个 `LogEvent.throwable`：

- 显式重载：`trace/debug/info/warn/error/fatal(String, Throwable)`。
- 隐式剥离：在 `LoggerImpl.log(Level, String, Object[], Throwable)` 中，
  若 `throwable == null` 且 `args` 末尾元素是 `Throwable`，则将其剥离为关联异常，
  剩余参数用于 `{}` 占位符填充。

这与 SLF4J/log4j2 的「varargs 末尾 Throwable 自动作为异常」语义一致，避免调用方
因多写一个参数而把异常塞进消息。

### 3.3 调用位置符（按需捕获）

调用位置（类/方法/行号/文件）需通过 `new Throwable().getStackTrace()` 捕获，**代价较高**。
因此并非每次日志都捕获，而是：

- `Layout` 在解析模式时，若模式含 `%C %M %L %F`（任一），将 `requiresCallerLocation` 置真；
- `LoggerImpl.needsCallerLocation()` 沿 additivity 链向上遍历，只要任一 appender 的布局
  需要位置即返回 `true`；
- `LoggerImpl.findCaller()` 跳过 `com.flora.runtime.log` 包，取首个业务栈帧；找不到时回退
  到最底层栈帧或 `null`。

无位置需求时（绝大多数生产布局），零额外开销。

### 3.4 相对时间 `%r`

以 JVM 启动时间为基准常量 `JVM_START`，转换时计算 `eventTime - JVM_START` 的毫秒数。
不依赖外部时钟同步，仅反映进程内相对耗时。

### 3.5 高亮 `%highlight`

`%highlight{...}` 内层按当前事件级别包裹 ANSI 转义码：TRACE/DEBUG 青、INFO 绿、
WARN 黄、ERROR 红、FATAL 粗红。选项解析使用 `findMatchingBrace` 以正确支持内层嵌套花括号。
颜色仅在终端（TTY）场景下有意义；写入文件时 ANSI 码会被原样写出，调用方应按需选择布局。

### 3.6 filePattern 滚动归档

- 基于大小（`SIZE_BASED`）：归档命名 `%i`，1 为最新；滚动时先把历史 `.i` → `.i+1` 顺移，
  删除超出 `maxHistory` 的最旧文件，再把当前文件移为 `.1`。
- 基于时间（`TIME_BASED`）：按 `%d{格式}` / `%d` 替换当日日期归档。
- 未设置 `filePattern` 时回退到旧命名：`base.log.日期` / `base.log.N`。
- 兼容性修复：归档路径与当前路径一律通过 `basePath.getFileSystem().getPath(...)` 或
  `basePath.resolveSibling(...)` 构造，不再使用 `Paths.get(...)`（默认文件系统），
  使滚动在任意文件系统（含内存虚拟文件系统）下行为一致。

### 3.7 滚动大小判定

缓冲写入（`BufferedWriter` + 通道缓冲）下 `Files.size(currentPath)` 不能可靠反映已写入
字节数，在内存文件系统中该问题尤为突出（数据仅在通道 `close()` 时写回节点）。因此
`RollingFileAppender` 自行累计 `currentSize`（每次 `append` 累加
`layout.format(event).getBytes(UTF_8).length`），`checkRoll`（SIZE_BASED）以
`currentSize >= maxSize` 为阈值，`roll()` 末尾重置为 0。

### 3.8 惰性求值 `Supplier<String>`

每个级别新增 `void xxx(Supplier<String> message)` 重载：仅当对应级别启用时调用
`message.get()`。典型用法：

```java
log.debug(() -> "expensive state=" + computeExpensive());
```

当 DEBUG 关闭时，`computeExpensive()` 与字符串拼接都不会执行，避免无谓开销。

## 4. 测试无落盘化（虚拟文件系统）

原日志文件测试使用 `Files.createTempFile` / `Files.createTempDirectory` 落盘，存在
临时目录清理、8.3 短路径解析等不稳定因素。本次改用项目自带的虚拟文件系统：

- 辅助方法 `newMemFs()`：`new VfsFileSystem()` 后 `fs.mount("/mem", new MemoryFileSystem())`；
- 测试中 `try (VfsFileSystem fs = newMemFs())`，通过 `fs.getPath("/mem/...")` 取得路径，
  构造 `FileAppender` / `RollingFileAppender`，结束调用 `appender.close()` 释放；
- 因 `MemoryFileSystem` 仅在通道 `close()` 时写回数据，测试在断言前必须 `close()`，
  否则读取不到内容——这与 3.7 的自管 `currentSize` 互补，共同使滚动在内存文件系统下可验证。

日志系统本身无需改动即可接入 VFS：它自始至终只通过 `Path` + `Files.*` 读写，
只要把路径来源从默认文件系统换成 VFS 提供的 `Path` 即可。本次为支持 `Path` 入参，
`FileAppender` / `RollingFileAppender` 增加了 `file(Path)` 与对应构造器，
并将内部路径解析改为文件系统无关写法。

## 5. 影响范围与回归

- 新增/修改文件：
  - `Level.java`（FATAL）、`Logger.java`（FATAL/异常/惰性重载）、
    `LogConfig.java`（`file(Path)`、`filePattern`）、
    `LogEvent.java`（throwable、callerLocation）、
    `Layout.java`（新转换符、调用位置、高亮）、
    `LoggerImpl.java`（异常剥离、调用位置捕获、惰性路由）、
    `FileAppender.java`（Path 入参）、
    `RollingFileAppender.java`（filePattern、文件系统无关路径、自管 currentSize）。
  - 测试：`LogTest.java`（新增 FATAL/异常/位置符/相对时间/高亮/filePattern/惰性 测试，
    原落盘测试改为 VFS）。
- 回归：`LogTest` 共 42 个用例全部通过，无落盘依赖。
- 向后兼容：`LogEvent` 旧构造器保留委托；`Logger` 既有的 `String` / `String,Object...`
  方法签名与行为不变。
