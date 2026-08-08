# 分类型共享线程池基础设施（ExecutorPools）

## 背景
`com.flora.common.SharedExecutors` 仅提供单一单线程刷新池，所有后台刷新串行排队于一个线程，
存在全局串行瓶颈与无界队列堆积风险。将其升级为按任务语义分池的共享执行器注册表。

## 决策（已与用户确认）
- **池划分维度**：按计算特征/语义，预置 `COMPUTE / IO / SCHEDULED / LIGHT` 四类，基础库内部固定。
- **参数来源**：基于 `Runtime.getRuntime().availableProcessors()` 动态计算 + 内置合理默认 + 有界队列；零依赖，不接配置系统。
- **生命周期**：纯守护线程，随 JVM 退出；不提供任何 shutdown/关闭 API（进程级单例）。
- **包位置**：新建 `com.flora.concurrent`（与 `com.flora.concurrent.retry` 并列），`module-info` 新增 `exports com.flora.concurrent;`，内部实现放不导出的 `com.flora.concurrent.impl`。

## 参数表（N = availableProcessors）
| 类型 | 适用 | core | max | 队列 | keepAlive | 拒绝策略 |
|------|------|------|-----|------|-----------|----------|
| COMPUTE | 加解密/压缩/编解码/哈希（CPU 密集） | N | N | ArrayBlockingQueue(cap=N*64,最小256) | 0 | CallerRuns |
| IO | 缓存刷新/远程调用/异步刷盘（阻塞、可重触发） | max(2,N) | N*2 | ArrayBlockingQueue(1024) | 60s | Discard |
| SCHEDULED | 定时/周期 | max(1,N/2) | 同 core | DelayedWorkQueue | — | Discard |
| LIGHT | 一次性短生命周期异步任务 | 0 | min(N*8,256) | SynchronousQueue | 60s | CallerRuns |

全部 `daemon=true`。

## 拒绝策略约定
- COMPUTE / LIGHT：CallerRuns，池饱和时由提交线程兜底，**不丢任务**。
- IO / SCHEDULED：Discard，池饱和时**静默丢弃**；提交即表示任务可重触发或丢失可接受
  （如缓存后台刷新由 `RefreshingCacheAdapter` 的 per-key 去重保证下次读取再触发）。

## API
- `enum TaskKind`：承载每类默认参数（前缀、core、max、queueCap、keepAlive、rejected）。
- `ExecutorPools.executor(TaskKind)`：返回窄接口 `Executor`（防外部分关闭）。
- `ExecutorPools.scheduled()`：返回 `ScheduledExecutorService`。
- `ExecutorPools.refresh()`：便捷方法，等价于 `executor(TaskKind.IO)`，供缓存后台刷新。
- 懒加载单例：`ConcurrentHashMap<TaskKind, ExecutorService>` + `computeIfAbsent`。
- `com.flora.concurrent.impl.FloraThreadFactory` + 重写 `afterExecute` 的 `LoggingThreadPoolExecutor` /
  `LoggingScheduledThreadPoolExecutor`：统一线程命名与守护属性，任务异常经 `runtime.log` 记录
  （标准线程池不会为提交任务触发线程的 UncaughtExceptionHandler，须在 afterExecute 兜底）。

## 改造
- 新增 `com/flora/concurrent/{ExecutorPools,TaskKind,package-info}.java` 与 `impl/{FloraThreadFactory,LoggingThreadPoolExecutor,LoggingScheduledThreadPoolExecutor}.java`。
- 删除 `com/flora/common/SharedExecutors.java`；`RefreshingCacheAdapter`、`Caches` 改用 `ExecutorPools`。
- `module-info.java` 新增 `exports com.flora.concurrent;`。

## 验证
- `flora-root` 测试全绿（2165 个，0 失败），`ExecutorPoolsTest` 覆盖单例性、守护/命名、CallerRuns 不丢、Discard 丢弃、异常不传播。
