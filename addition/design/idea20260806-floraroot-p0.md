# 将 openhanako 重写所需的 P0 通用能力沉淀到 flora-root — 设计方案

日期：2026-08-06
主题：把 openhanako（Electron+Node+React 的个人 AI Agent 桌面框架）用 Java 重写时，那些「与特定业务场景无关」的底层通用能力，优先把风险最低、纯逻辑、几乎必用的 7 项（P0）沉淀进 `flora-root`。本文给出包划分、核心 API、行为语义、与现有 flora-root 的衔接点，以及落地顺序。

## 0. 背景与范围

- **目标**：openhanako 的通用基础设施（事件总线、错误模型、重试、日志脱敏、安全解析、版本比较等）是纯逻辑、业务无关、每个模块都会用到的。把它们做成 `flora-root` 的零依赖公共能力，避免重写时各模块重复造轮子。
- **范围（本文）**：只覆盖 P0（纯逻辑、易移植、低风险）。openhanako 里依赖 Node/Electron 平台 API 的 P1 能力（插件框架、受限 HTTP、文件安全写、Cron、CLI 等）与平台专有 P2 能力（Electron IPC、electron-updater、OS 沙箱）不在本文实现，仅在与 P0 有衔接处说明边界。
- **约束（沿用 flora-root 既定规则）**：
  - 运行时零外部依赖（仅 JDK + 既有自研）；不引入 Spring/picocli/Guava 等。
  - JPMS：`module-info.java` 仅 `exports` 公开 API 包；内部实现放 `impl` 子包。
  - 仅通过 `module-info.java` 的 `exports` 导出供外部消费的包。

## 1. P0 能力清单与包划分

| P0 能力 | 来源（openhanako） | 落地包 | 新增导出包 |
|---|---|---|---|
| 统一错误模型 `AppException` | `shared/errors.ts` | `com.flora.runtime.error` | `com.flora.runtime.error` |
| 事件总线 `EventBus`（pub/sub + request/response + capability 目录） | `hub/event-bus.ts` | `com.flora.runtime.bus` | `com.flora.runtime.bus` |
| 错误总线 `ErrorBus` | `shared/error-bus.ts` | `com.flora.runtime.bus` | （同上，同包） |
| 重试 `Retry`（去相关抖动退避） | `shared/retry.ts` | `com.flora.runtime.retry` | `com.flora.runtime.retry` |
| 日志脱敏 `LogRedactor` | `shared/log-redactor.ts` | `com.flora.runtime.log` | （已有包，加类） |
| 安全 JSON 解析 `JsonSafe` | `shared/safe-parse.ts` | `com.flora.codec.json` | （已有包，加类） |
| 语义化版本 `Version` | `lib/plugin-versioning.ts` | `com.flora.codec.version` | `com.flora.codec.version` |

> 全部基于 JDK（`java.base` + `java.util.concurrent`），不新增 `requires`。`module-info.java` 需补 3 个新导出包：`com.flora.runtime.error`、`com.flora.runtime.bus`、`com.flora.runtime.retry`、`com.flora.codec.version`。

## 2. 逐项设计

### 2.1 统一错误模型 — `com.flora.runtime.error`

**目标**：用单一异常基类承载「错误码 → severity / category / i18nKey / retryable / httpStatus」的映射，支持 `cause` 链与 JSON 序列化，供事件总线、错误总线、各模块统一使用。

**核心类型**：
- `enum Severity { DEBUG, INFO, WARNING, ERROR, CRITICAL }`（与 `com.flora.runtime.log.Level` 对齐，可双向映射）。
- `final class ErrorCode`：不可变值对象，字段 `code`(String)、`severity`、`category`(String)、`i18nKey`(String)、`retryable`(boolean)、`httpStatus`(int)。
- `class AppException extends RuntimeException`：
  - 构造：`AppException(ErrorCode code, String message, Throwable cause)`；
  - 工厂：`AppException.of(ErrorCode)`、`AppException.of(String code, String message)`（按注册表查元数据，缺失则用默认）、`AppException.wrap(Throwable, ErrorCode)`；
  - 序列化：`String toJson()` / `static AppException fromJson(String)`；
  - 访问器：`ErrorCode errorCode()`、`boolean isRetryable()`、`Severity severity()`。
- `final class ErrorCodes`：内置常用码（`BUS_NO_HANDLER`、`BUS_TIMEOUT`、`RETRY_EXHAUSTED`、`CONFIG_INVALID`、`IO_ATOMIC_WRITE_FAILED` 等），并允许应用用 `ErrorCodes.register(ErrorCode)` 扩展。

**衔接**：`EventBus` 的 `BusNoHandlerException` / `BusTimeoutException`（见 2.2）直接继承 `AppException` 并使用 `ErrorCodes` 中的标准码，保证全链路错误可统一序列化与路由。

### 2.2 事件总线 — `com.flora.runtime.bus`

**目标**：进程内、同步分发的发布订阅总线，支持（1）按 `type` 与可选 `sessionPath` 过滤的订阅；（2）请求/响应模式（带超时、链式短路）；（3）声明式能力目录。

**核心 API**：
```java
public final class EventBus {
    public interface Event { String type(); String sessionPath(); Object payload(); }
    public interface Subscription { void cancel(); }

    public Subscription subscribe(String type, Consumer<Event> handler);
    public Subscription subscribe(String type, String sessionPath, Consumer<Event> handler);

    public void emit(String type, Object payload);
    public void emit(String type, String sessionPath, Object payload);

    // 请求/响应
    public interface Request  { String type(); Object payload(); }
    public interface Response { Object body(); }
    public static final Object SKIP = new Object(); // handler 返回 SKIP 则交给下一个 handler

    public Registration handle(String type, Function<Request, Response> handler);
    public CompletableFuture<Response> request(String type, Object payload, Duration timeout);

    // 异常
    public static class BusNoHandlerException extends AppException { ... }
    public static class BusTimeoutException    extends AppException { ... }
}

public final class CapabilityDirectory {
    public void registerCapability(String id, Object impl);
    public <T> T getCapability(Class<T> type);          // 按类型取
    public <T> T getCapability(String id, Class<T> type); // 按 id 取
    public List<String> listCapabilities();
}
```

**行为语义**（对齐 `hub/event-bus.ts`）：
- `emit` 同步遍历匹配订阅：先全局订阅（`sessionPath == null`），再 `sessionPath` 命中订阅；索引化（`Map<type, List<sub>>` + `Map<sessionPath, List<type>>`）避免全量扫描。
- `request` 顺序尝试 `handle` 注册的 handler；handler 返回 `SKIP` 则继续下一个；无 handler → 完成后 `completeExceptionally(BusNoHandlerException)`；超时不返回 → `BusTimeoutException`。
- 分发在当前调用线程同步执行（与 openhanako 一致）。**异步派发 + 线程池封装属 P1，不在 P0**；P0 仅用 JDK `CompletableFuture` 承载 `request` 的 Future 语义。

### 2.3 错误总线 — `com.flora.runtime.bus`（同包）

**目标**：单例错误聚合，支持面包屑、按 `dedupeKey` 去重（5s 窗口）、多监听器订阅。

**核心 API**：
```java
public final class ErrorBus {
    public record ErrorRecord(Throwable error, Severity severity,
                              String route, long ts, List<String> breadcrumbs) {}

    public void addBreadcrumb(String crumb);
    public void report(Throwable error);                       // 默认 route=null
    public void report(Throwable error, String route, String dedupeKey);
    public Subscription subscribe(Consumer<ErrorRecord> listener);
}
```

**行为语义**（对齐 `shared/error-bus.ts`）：`report` 时若 5s 内出现过相同 `dedupeKey` 则抑制重复；`severity` 取自 `AppException`（非 AppException 默认 `ERROR`）。UI/日志侧的「按 severity 路由到 statusbar/toast」是应用职责，flora-root 只提供 `ErrorRecord` + `Severity`，不耦合 UI。

### 2.4 重试 — `com.flora.runtime.retry`

**目标**：可中断、可定制「是否重试」、去相关抖动（decorrelated jitter）退避的通用重试工具。

**核心 API**：
```java
public final class Retry {
    public interface Backoff { Duration next(int attempt, Duration prev); }
    public static <T> T withRetry(Callable<T> task, Options opts) throws AppException;
    public static void withRetry(Runnable task, Options opts) throws AppException;

    public static final class Options {
        public Options maxAttempts(int n);
        public Options base(Duration d);          // 基准延迟
        public Options cap(Duration d);           // 上限
        public Options signal(InterruptedException::class 中断源); // 可取消
        public Options shouldRetry(Predicate<Throwable> p); // 默认：仅 AppException.isRetryable()
    }
    // 去相关抖动：next = min(cap, random(base, prev*3))
    public static Backoff decorrelatedJitter();
}
```

**行为语义**（对齐 `shared/retry.ts`）：第 1 次延迟 ∈ `[base, base*3)`；之后 ∈ `[base, prev*3)` 截到 `cap`；`signal` 触发则抛中断；`shouldRetry` 返回 false 立即包装为 `ErrorCodes.RETRY_EXHAUSTED` 的 `AppException`。纯 JDK，无依赖。

### 2.5 日志脱敏 — `com.flora.runtime.log`（加类）

**目标**：在日志落盘/输出前，对消息与参数做密钥/PII 正则掩码，防敏感信息泄漏。

**核心 API**：
```java
public final class LogRedactor {
    public static final LogRedactor DEFAULT = new LogRedactor(defaultRules());
    public String redactText(String text);
    public Object redactValue(Object value);        // 递归处理集合/Map，深度上限 8，循环引用保护
    public Object[] redactArgs(Object[] args);      // formatLogArgs 等价物
    public LogRedactor withRule(RedactionRule r);   // 追加规则
}
public interface RedactionRule { String redact(String text); }
```

**默认规则**（对齐 `shared/log-redactor.ts` 的正则集）：API key、Bearer token、URL 内 `user:pass@` 凭证、信用卡号、身份证号、邮箱、长随机串（≥阈值）、home 目录路径匿名化为 `~`（对应 Java `user.home`）。`redactValue` 对 `Map`/`Collection`/数组递归脱敏，深度达 8 或遇已访问对象则停止。

**衔接（关键）**：需对既有 `com.flora.runtime.log` 做**纯增量**接线——`MessageFormatter`（`com.flora.runtime.log.impl`）在格式化前若 `Logger` 持有 `LogRedactor` 则先脱敏；`LoggerFactory`/构造器增加可选 `redactor` 入参，默认用 `LogRedactor.DEFAULT`。不改动现有 `Logger` 既有行为（未设置 redactor 时等同原样输出）。

### 2.6 安全 JSON 解析 — `com.flora.codec.json`（加类）

**目标**：解析失败时返回 fallback 而非抛异常，并上报 `ErrorBus`（若存在）。

**核心 API**：
```java
public final class JsonSafe {
    public static Object parse(String text, Object fallback);
    public static <T> T parse(String text, Class<T> type, T fallback); // 复用 JsonUtil 反序列化
    public static Object parseResponse(byte[] body, Object fallback);  // 等价于 safeParseResponse
}
```

**行为语义**（对齐 `shared/safe-parse.ts`）：`JsonParser` 抛错时记录 `ErrorBus.report(...)`（ErrorBus 未初始化则仅静默回退），返回 `fallback`。复用既有 `com.flora.codec.json.JsonParser`/`JsonUtil`，不重写解析器。

### 2.7 语义化版本 — `com.flora.codec.version`

**目标**：解析、比较、判定兼容性的纯算法版本工具（SemVer 风格）。

**核心 API**：
```java
public final class Version implements Comparable<Version> {
    public static Version parse(String s);             // 容错：缺省补 0
    public int compareTo(Version o);
    public boolean gte(Version o);
    public boolean isCompatible(Version required);     // 主版本相同且 >= required
    public String raw();
}
```

**行为语义**（对齐 `lib/plugin-versioning.ts`）：`parse` 容错补全缺失段；`compareTo` 按 主.次.修订 数值比较；`isCompatible` 实现 `isVersionCompatible`（主版本锁、次/修订向上兼容）。仅依赖 `com.flora.codec.json` 之外的纯 JDK，零依赖。

## 3. module-info 变更

```java
exports com.flora.runtime.error;
exports com.flora.runtime.bus;
exports com.flora.runtime.retry;
exports com.flora.codec.version;
// 以下已存在，无需改动：com.flora.runtime.log, com.flora.codec.json
```
不新增 `requires`（全部 JDK 自带）。`com.flora.runtime.bus` 内部若需 `CompletableFuture` 仅为 `java.base` 内容。

## 4. 与现有 flora-root 的衔接点汇总

| 新增能力 | 复用的既有能力 | 需增量接线的既有代码 |
|---|---|---|
| `AppException` | —— | 既有的 root 异常（若有）改为继承或保持独立；建议新异常统一以 `AppException` 为基 |
| `EventBus`/`ErrorBus` | `AppException`(2.1) | —— |
| `Retry` | `AppException` | —— |
| `LogRedactor` | `com.flora.runtime.log` | `MessageFormatter`(`impl`) 增加可选脱敏；`LoggerFactory` 增加 redactor 入参 |
| `JsonSafe` | `com.flora.codec.json.JsonParser`/`JsonUtil` | —— |
| `Version` | —— | —— |

> 注意：`module-info.java` 现有悬挂导出 `com.flora.module`（无对应源码）。规划插件框架（P1）时会与之冲突，建议本次顺手确认其来源（疑似 ramet 生成产物），P0 阶段暂不动，但在设计插件框架前必须清理。

## 5. 测试策略

每个能力单测对齐 openhanako 既有行为：
- `EventBus`：`subscribe`/`emit` 的 `sessionPath` 过滤；`request` 正常返回、`SKIP` 链式、`BusNoHandlerException`、`BusTimeoutException`（用极小 timeout）。
- `ErrorBus`：面包屑累积；相同 `dedupeKey` 5s 内抑制；不同 listener 均收到。
- `Retry`：成功直接返回；`shouldRetry=false` 立即失败；去相关抖动延迟落在 `[base, prev*3)` 且 ≤ `cap`；`signal` 中断。
- `LogRedactor`：各默认规则命中掩码；`redactValue` 递归深度/循环引用不爆栈；home 路径转 `~`。
- `JsonSafe`：非法 JSON 返回 fallback 且上报 ErrorBus；合法 JSON 正常解析。
- `Version`：`parse` 容错补全；`compareTo`/`gte`/`isCompatible` 排序与兼容判定正确。

## 6. 落地顺序建议

1. `com.flora.runtime.error`（`AppException`/`ErrorCode`/`Severity`）—— 地基，被其余多项依赖。
2. `com.flora.runtime.bus`（`EventBus` + `CapabilityDirectory` + `ErrorBus`）—— 依赖 error。
3. `com.flora.runtime.retry` —— 依赖 error。
4. `com.flora.runtime.log` 增量接线 `LogRedactor` —— 纯增量，风险低。
5. `com.flora.codec.json` 加 `JsonSafe` —— 复用解析器。
6. `com.flora.codec.version`（`Version`）—— 独立。

## 7. 开放问题与边界

- **同步 vs 异步**：P0 事件总线为同步分发（对齐 openhanako）。异步派发、线程池/调度封装属 P1，不在本文。
- **`AppException` 字段取舍**：`i18nKey`/`httpStatus` 是否常驻？建议保留为可选字段（框架中性，应用按需填充）。
- **`ErrorBus` 与 UI 路由**：flora-root 只产出 `ErrorRecord` + `Severity`，路由到 statusbar/toast 由应用层做。
- **平台专有能力不入 flora-root**：Electron IPC、WebSocket 传输、`electron-updater`、OS 级沙箱由 Java 桌面宿主另行实现，仅借用其「invoke 语义 + 错误归一」「访问分级 + capability 校验」等模式。
