# 代码审查：com.flora.cache 包

- 审查日期：2026-07-25
- 审查范围：`flora-root/src/main/java/com/flora/cache`（含 `store`、`eviction` 子包，共 16 个文件）
- 审查基线：提交 `f676aaa`（容量下放 + 冗余清理之后）
- 审查结论：**整体结构清晰（ISP 分层、策略插件化、异常隔离到位），发现 1 处需修复的容量失守缺陷（F1）、1 处高缺失场景下的质量缺陷（F2），其余为低危/信息项。**

---

## 一、正面评价

- 接口正交分层干净：`Cache` → `ObservableCache`/`EvictableCache` → `BoundedCache`，能力按需 opt-in，符合接口隔离原则。
- 淘汰策略为纯插件（`EvictionPolicy` 只决策、不碰存储），引擎 `CacheSupport` 通过 `rawXxx` 钩子 + `afterWrite()` 模板方法承担删除与事件派发，职责单一。
- `fire()` 对监听器异常做隔离（`CacheSupport.java:270`），单个监听器故障不影响主流程，符合缓存可观测性的健壮性要求。
- Javadoc 聚焦接口自身核心约定，注释与代码行为一致。

---

## 二、缺陷（按严重度）

### F1【中/高】W-TinyLFU：准入被拒时受害者泄漏，极端场景可导致容量无限超出

位置：`WTinyLfuEvictionPolicy.java:317-365`（`pickMainVictim` + `evictFromWindow`）。

`pickMainVictim()` **无条件**把受害者从 `probation` 摘除并置 `region=DETACHED`；随后在 `evictFromWindow()` 的 else 分支（候选频率不占优，即「受害者胜出」）中，代码只返回候选让引擎删除候选，受害者**留在后端存储但已不在任何分段、标记为 DETACHED**。作者注释称「与原实现一致：泄漏出索引，不再追踪」。

问题链条：

1. 每一次「受害者胜出」都会使 `probation` 少一项、存储却未少对应项（候选被删，受害者留下）→ `probation` 逐渐抽空。
2. 当 `probation` 被抽空后，唯一能兜底的强删路径 `evict()` 第 239 行 `if (sizeOf.getAsLong() > capacity) { pollEldest(probation...) }` 因 `probation` 为空而返回 `null`。
3. 此时若 `sizeOf == capacity`，`evict()` 在窗口分支（需 `windowSize > windowMax`）与兜底分支（`sizeOf > capacity`）均不成立，直接返回 `null`。
4. 引擎 `BoundedCacheSupport.afterWrite()` 的 `while (... (victim = p.evict()) != null)` 立即终止；随后 `CacheSupport.put` 的 else 分支执行 `rawPut` 加入新项 → `sizeOf` 变为 `capacity+1`。
5. 下一轮 `sizeOf > capacity` 成立，但 `probation` 仍空 → 兜底再次失败 → `evict()` 返回 `null` → 继续 `rawPut` → **容量无界增长**。

常规负载下（候选多被准入、probation 不空）缓存仅会在 `capacity`~`capacity+1` 间抖动，兜底可正常回收；但当存在持续准入拒绝（候选频率长期不占优）时，会触发上述无界增长，属于真实健壮性缺陷。

建议修复（保持语义：受害者胜出则保留受害者）：在 `evictFromWindow()` 的 else 分支中，既然受害者胜出，应将其**重新放回 `probation`** 而非置 `DETACHED`，只删除候选：

```java
} else {
    // 候选频率不占优：受害者胜出，保留回 probation；仅删除候选
    region.put(victim, R_PROBATION);
    probation.put(victim, victim);
    return candidate;
}
```

这样 `probation` 不会被抽空，兜底路径长期有效，容量可维持为硬上限。

---

### F2【中】缺失键命中计数 + 自愈 `onPut` 导致窗口被幻影键污染，高缺失场景产生无效淘汰

位置：`CacheSupport.java:133-137`（`get` 对命中/未命中均调 `onAccess`）、`WTinyLfuEvictionPolicy.java:177-204`（`onAccess` 中 `region==null` 走 `onPut` 自愈）。

- `CacheSupport.get` 注释明确「命中 / 未命中都计入频率」——这是有意设计，本身可接受。
- 但 `WTinyLfuEvictionPolicy.onAccess` 当 `region.get(key) == null` 时**直接调用 `onPut(key)` 把该键重新塞入 window 段**（而不仅是累加 sketch 频率）。`region==null` 不仅对「并发摘除兜底」成立，也对「从未写入的缺失键」「已被淘汰（引擎 `onRemove` 已清 region）的键」成立。

后果：

- 高缺失（high-miss）工作负载下，任意 `get` 未命中都会把缺失键注入仅占 ~1% 容量的 window 段，并累加 sketch 频率。
- 一个被频繁访问的缺失键会积累高 sketch 频率，在准入比较时可能**击败真实驻留的 probation 受害者**，导致引擎 `rawRemove(候选)` 删掉一个根本不在存储中的幻影键（返回 `old==null`，仍派发 `EVICT`/`INVALIDATE` 事件），同时把真实驻留项挤出。
- 即：高缺失负载会劣化命中率并产生无意义的淘汰与事件噪声。

建议（二选一，倾向前者，影响更小）：
- 收窄自愈：当 `region==null` 时只 `sketch.increment(key)` 做频率记账，**不要重新入 window**；或把自愈限定为「确实在存储中」的情形（策略侧无法查存储，可由引擎在 `get` 命中时才调 `onAccess` 来缓解）。
- 或调整 `CacheSupport.get`：仅命中（`rawGet != null`）时调 `onAccess`，miss 不计入策略（与「命中/未命中都计频率」的当前约定相悖，需权衡）。

此项与 F1 可一并评估，但属于质量（命中率）问题，严重度低于容量失守。

---

### F3【低】FrequencySketch 与 LFU 计数器的非原子读写（近似结构下的良性数据竞争）

- `FrequencySketch.increment`：`if (get(idx) < MAX_COUNT) set(idx, get(idx) + 1);` 与 `reset()` 之间、`estimate` 之间均无同步；`count` 字段非 `volatile`/非原子（`++count`）。并发下会丢失计数更新、aging 触发时机略有偏差。
- `LFUEvictionPolicy.evict()`：直接 `e.getValue()[0]` 读取 `ConcurrentHashMap` 中共享的 `long[]` 元素，而 `onAccess` 的 `computeIfPresent` 在 bin 锁内原地 `v[0]++`。读取发生在锁外，构成对 `long` 数组元素的数据竞争（JMM 不保证 `long` 数组元素读写的原子性/可见性）。

对于「近似」频率统计，单点丢失/撕裂通常可接受；但若要求严格，可将 `long[]` 改为 `AtomicLong`、或 `FrequencySketch` 的 `count` 改用 `AtomicLong`、`reset`/`increment` 加 `synchronized` 或 `LongAdder`。建议至少把 `count` 改为 `AtomicLong` 并在 `reset` 处加锁，消除最明显的竞争。

---

### F4【低】`BoundedCacheSupport.gc()` 返回值未计入被淘汰项

位置：`BoundedCacheSupport.java:42-46`。`gc()` 仅返回 `sweepExpired()` 的过期清理数，`afterWrite()` 淘汰掉的项被丢弃。文档称「返回被清理的数量」，语义上淘汰项也应计入。建议 `afterWrite()` 返回被淘汰数量并由 `gc()` 累加（需把 `afterWrite()` 改为返回 `long`；注意 `CacheSupport.afterWrite()` 当前返回 `void`，子类 `BoundedCacheSupport` 覆写，改动需同步）。

---

### F5【低】`gc()` 对过期扫描重复调用

`BoundedCacheSupport.gc()` 先调 `sweepExpired()`，再调 `afterWrite()`，而 `afterWrite()` 内部第 65 行又调一次 `sweepExpired()`。即一次 `gc()` 扫描过期两遍，纯属冗余开销（功能无错）。可考虑让 `afterWrite()` 不再内部扫描、由调用方决定是否扫描，或 `gc()` 只调 `afterWrite()`。

---

### F6【信息/低】FIFO/LRU `evict()` 与引擎 `onRemove` 的重复摘除

`FIFOEvictionPolicy.evict()` / `LRUEvictionPolicy.evict()` 在返回受害者前已从内部 `LinkedHashMap` 摘除该键；引擎随后 `rawRemove` → `onRemove` → `policy.onRemove` 又 `remove` 一次（no-op）。无害，仅冗余。LFU 同理（`evict()` 内 `freq.remove(best)`，引擎 `onRemove` 再 `remove` 一次）。

---

### F7【信息】`put` 覆盖清除 TTL 行为正确，但与 `setTtl` 缺键语义需知晓

- `CacheSupport.put(K,V)`（无 duration）更新分支 `rawPut` 覆盖并清除原 TTL（`MemoryCache.rawPut` 第 58-61 行 `expiry.remove(key)`），与 `Cache` 接口注释「覆盖并清除原 TTL」一致，行为正确。
- `CacheSupport.setTtl` 对不存在的键走 else 分支执行 `rawSetTtl`（实现侧静默忽略或 Redis 上无效果），与 `Cache` 注释「key 不存在由实现决定」一致。非缺陷，仅提示调用方注意该语义。

---

## 三、建议优先级

| 编号 | 严重度 | 是否建议修复 |
|------|--------|--------------|
| F1   | 中/高  | **建议修复**（容量硬保证） |
| F2   | 中     | 建议修复或收窄（命中率） |
| F3   | 低     | 可选（近似结构可接受） |
| F4   | 低     | 可选（语义完善） |
| F5   | 低     | 可选（性能） |
| F6   | 信息   | 无需 |
| F7   | 信息   | 无需 |

---

## 四、测试覆盖提示

当前 `flora-root` 全量 1180 测试通过。建议针对 F1 补充一个**持续准入拒绝**的回归测试：构造一个候选频率长期不占优的工作负载（如交替访问两组键，A 组常驻、B 组反复 miss），断言在反复 `put` 后 `approxCount()` 始终 ≤ `capacity`（或在概率意义上不无界增长）。
