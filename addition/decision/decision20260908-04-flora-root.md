# 决策：Argon2 按 lane 并行填充

- 日期：2026-09-08
- 模块：flora-root（`com.flora.root.crypto.Argon2`）
- 状态：已采纳

## 背景

设置页调整 Argon2 并行度（4→8→16→64）后，「测试」按钮给出的建议迭代次数与耗时都不变。排查结论：

1. 参数读取没问题（`MasterKdfPanel.runBenchmark()` 点击时实时读字段）。
2. 「≈1 秒」是设计使然：`Argon2IterationProbe.hintText()` 输出的是「使总耗时最接近 1 秒的迭代数 n」及其总耗时，故恒定接近 1 秒。
3. 真正原因：`Argon2` 是单线程实现，`parallelism`（lane 数）只参与内存分 lane 布局与引用索引，不产生任何加速。

实测（64 MiB / 1 迭代，取 5 次最小值）：p=1/4/8/16/64 的单次耗时均为约 0.20s。

## 决策

**按 lane 并行填充，每个 (pass, slice) 内各 lane 并行，slice 之间屏障。串行与并行共用同一个 `fillSegment`。**

### 正确性依据

主循环为 `pass → slice(4) → lane → j`。查看 `indexAlpha`（`Argon2.java`）中引用块的取值区间：

| 情形 | 引用区间 | 跨 lane 依赖 |
|---|---|---|
| pass=0, slice=0 | `refLane` 被强制为当前 lane | 无 |
| pass=0, slice>0, 跨 lane | `[0, slice*segmentLength)` | 只指向**已完成的 slice** |
| pass>0, 跨 lane | `[start, start+3*segmentLength)`，`start=(slice+1)*segmentLength` | 恰为**除当前 slice 外**的 3 个 slice |

故同一 (pass, slice) 内各 lane 之间无数据依赖，可安全并行。

两条硬约束：

1. 同 lane 分支的 `referenceAreaSize` 含 `within-1`，引用会延伸到本 slice 的已算前缀 → **一条 lane 在一个 (pass,slice) 的整个 segment 必须由同一线程按 j 递增顺序计算，绝不可按 j 拆分任务**。
2. pass>0 时 `blocks` 原地覆盖（引用可能取到上一 pass 残留值），但该残留值在上一 slice 屏障前已定稿，不破坏上述结论。

### 线程池：专用惰性 ForkJoinPool，不用 commonPool

| 方案 | 结论 |
|---|---|
| `ForkJoinPool.commonPool()` | 否决。单次 0.2~5s，占满 commonPool 会饿死同进程其它任务；调用方可能本身就在 commonPool worker 内，嵌套会触发补偿线程、加速比塌掉。 |
| `InternalExecutors.compute()` | 否决。只接受 `Runnable`、不返回 Future，无法做屏障。 |
| **专用惰性 ForkJoinPool** | **采纳**。parallelism = `availableProcessors`；守护线程、命名 `flora-argon2-N`；holder 惰性初始化，不提供关闭（与 `InternalExecutors` 惯例一致）。fork/join 的 join 是 managed 的，池满时不会像 `CountDownLatch` 那样死锁；fork/join 还提供 happens-before，天然满足 slice 屏障的内存可见性。 |

注意：`ForkJoinPool` 构造器要的是 `ForkJoinWorkerThreadFactory`，不能直接复用 `FloraThreadFactory`（那是 `ThreadFactory`），故在 `Argon2` 内基于默认工厂包一层仅改名并置为守护线程。

调度：单个 root `RecursiveAction` 遍历 pass/slice，每个 slice 内把 lane 连续分组（组数 = `min(lanes, pool.getParallelism())`，lane 不跨组）后 `invokeAll`，`invokeAll` 即该 slice 的屏障。整个填充只调一次 `POOL.invoke(root)`——必须走 `POOL.invoke`，否则静态 `ForkJoinTask.invokeAll` 在外部线程会落到 commonPool。

### 触发条件与回退开关

```java
parallel = lanes >= 2
        && availableProcessors >= 2
        && mPrime >= PARALLEL_MIN_BLOCKS   // 4096（4 MiB）
        && segmentLength >= PARALLEL_MIN_SEGMENT; // 128
```

阈值依据：约 3µs/块，`segmentLength=128` ≈ 0.4ms/任务，远大于 fork/join 与屏障开销（µs 级）。官方向量（m=32/64）与 m=1024/p=4 自动走串行。

**回退**：把 `PARALLEL_MIN_BLOCKS` 改为 `Integer.MAX_VALUE` 即完全回到串行（单点火控）。

### 附带修复

`zeroBlock` 的惰性初始化存在数据竞争（多线程可能各自创建）→ 改为 `private static final byte[] ZERO_BLOCK`。`fillBlock` 只读 prev/ref，共享安全。

## 验证

### 逐字节一致性（最关键）

改动**之前**先用串行实现固化 golden 值，改动后比对，7 组参数 × {d, i, id} 三型 = 21 个用例全部一致。参数覆盖并行路径（m=4096/p=2、m=8192/t=2/p=4、m=16384/p=8、m=65536/p=4、m=65536/t=2/p=64）与串行回退路径（p=1、p=64 且 segmentLength=32）。

回归：`Argon2KDFTest`（5，含官方 d/i/id 向量）、`KdbxOfficialVectorTest`（2）、`VaultUnlockerTest`（4）全通过。

新增测试：

- `flora-root/src/test/java/com/flora/root/crypto/Argon2Test.java`（快测）：小参数串行路径 KAT。
- `Argon2ParallelTest.java`（`@Tag("slow")`）：golden 矩阵 + 8 线程并发压测 + ForkJoin worker 内嵌套调用（均带 `assertTimeoutPreemptively` 兜底，屏障写错会超时失败而非挂死）。

### 性能（8 核，64 MiB / 1 迭代，多次取最小值）

| 并行度 | 改前 | 改后 | 加速 |
|---|---|---|---|
| 1（串行） | 0.197s | 0.207s | 1.0x（基线，无回退） |
| 4 | 0.202s | 0.082s | 约 2.5x |
| 8 | 0.196s | 0.065s | 约 3.0x |
| 16 | 0.211s | 0.083s | 约 2.5x |
| 64 | 0.198s | 0.082s | 约 2.4x |

加速比低于核数属正常：Argon2 是内存带宽敏感算法。

**对用户可见的效果**：设置页建议迭代数 n 由「恒定 5」变为随并行度变化（p=1→5，p=4→10~12，p=8→10~15），原先「调并行度没反应」的困惑随之消解。

### 踩到的坑

抽出 `fillSegment` 后，内层循环从读 `digest()` 的局部变量变成读 `FillMemory` 的字段，串行路径一度回退约 13%（0.197s→0.223s）。在 `fillSegment` 开头把字段导入局部变量后恢复（0.207s）。

## 风险

- 最大风险是任务粒度切错（按 j 拆）导致**静默结果错误**——已由 golden 矩阵 + 三型 + t≥2 覆盖兜住。
- 并行下 `fillBlock` 每块分配约 2.6KB，GC 压力上升，高 p 时加速比可能不足预期。
- 专用池线程常驻（守护线程，随 JVM 退出），与 `InternalExecutors` 现状一致。
