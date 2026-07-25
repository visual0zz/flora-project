# 代码审查：cache 包

- 日期：2026-07-25
- 范围：`flora-root/src/main/java/com/flora/cache/**`（含 `store`、`eviction` 子包）
- 审查维度：逻辑正确性、功能分工合理性、功能与名称一致性
- 结论：**整体架构清晰、分层合理**，但发现若干逻辑缺陷（含 2 个较严重的契约/语义 bug）与命名/文档不一致，建议修复。

## 一、包结构与分工概览

```
cache/
  Cache / EvictableCache / ObservableCache / BoundedCache  能力契约（接口 mixin）
  CacheEventType / CacheEventListener                      事件定义
  EvictionPolicy                                          淘汰策略 SPI
  store/
    RawStore         存储 SPI（真正 KV/TTL 读写）
    CacheEngine      通用编排（读写/TTL/事件/可选淘汰/可选容量），组合 RawStore
    MemoryCache      实现 RawStore + 组合 CacheEngine，默认挂 W-TinyLFU
    RemoteCache      抽象远程缓存（Redis 语义），组合无容量/无策略的 CacheEngine
  eviction/
    WTinyLfu / LRU / LFU / FIFO                            策略实现
```

模式为「接口分层 + SPI + 委托编排」，职责边界总体清晰。`MemoryCache`/`RemoteCache` 各自实现 `RawStore` 并委托 `CacheEngine`，是合理且可扩展的设计。

## 二、逻辑正确性

### 【高】1. UPDATE / MUTATE 事件的 `oldValue` 返回的是新值而非旧值

`CacheEngine.java:53-58`（以及 `:100-105` 的带 TTL 分支）：

```java
if (store.rawContains(key)) {
    store.rawPut(key, value);                 // 已覆盖写入新值
    ...
    fire(CacheEventType.UPDATE, key, () -> store.rawGet(key), () -> value);
    //                               ^^^^^^^^^^^^^^^^^^^^^^^^ 此时 store 已是新值
}
```

`oldValue` 供应商 `() -> store.rawGet(key)` 在 `rawPut` 之后求值，取到的是**更新后的值**。`CacheEventListener` 文档明确 `oldValue` = "操作前的值"，此处语义相反——任何监听 UPDATE/MUTATE 的监听器拿到的旧值都是错的。

`REMOVE/EXPIRE/EVICT` 路径正确地用 `() -> old`（删除前捕获）传入，唯独写路径用了惰性 `rawGet`，自相矛盾。

修复：在 `rawPut` 前捕获旧值，例如 `V prev = store.rawGet(key);` 再 `fire(UPDATE, key, () -> prev, () -> value)`。注意 `rawContains(key)` 为 true 时 key 必然未过期，`rawGet` 能取到真实旧值。

> 备注：`git log` 显示本地提交 `48320fe`（"修正 put 覆盖旧值快照"）曾处理过该点，但被拉取的 `bb0c705` 覆盖回 `Supplier + rawGet` 实现，故当前磁盘代码仍带此 bug，且测试（`CacheEventTypesTest`）未断言 `oldValue` 内容，未被发现。

### 【高】2. `MemoryCache.ttl(key)` 对不存在的 key 返回 `ZERO` 而非 `null`

`MemoryCache.java:124-129`：

```java
public Duration rawTtl(K key) {
    Long exp = expiry.get(key);
    if (exp == null) return Duration.ZERO;   // 既包含“存在但永不过期”，也包含“key 根本不存在”
    ...
}
```

`expiry` 中无记录既可能因为「key 存在且永不过期」（`Cache.ttl` 约定返回 `ZERO`，正确），也可能因为「key 根本不存在」（`Cache.ttl` 约定返回 `null`，错误）。`rawTtl` 没有用 `map.containsKey(key)` 区分两者，违反 `Cache.ttl` 的接口契约。

对照 `RemoteCache.rawTtl`（`:209-214`）正确处理了 `-2 → null`，说明契约本意是「不存在返回 null」。

修复：`if (!map.containsKey(key)) return null;` 后再判断 `exp == null → ZERO`。

### 【中】3. `putIfAbsent` 命中「已过期但未清除」的 key 时不写入，却留下僵尸条目

引擎 `putIfAbsent` 先用 `rawContains(key)` 判断存在性（过期键返回 false），进入插入分支后调用 `MemoryCache.rawPutIfAbsent`：

```java
// MemoryCache.java:87-93
return map.computeIfAbsent(key, _ -> { ... }) == value;
```

`map.computeIfAbsent` 只看 `map` 的物理存在，**不感知过期**。当 key 已过期但仍残留在 `map` 中时，`computeIfAbsent` 不插入、返回旧值 → `== value` 为 false → `putIfAbsent` 返回 false；但引擎此前已按「不存在」分支执行，未触发 INSERT 事件，且陈旧过期条目继续留在存储中。`get` 仍返回 null，逻辑上该 key「既不存在又写不进去」。

同理 `putIfAbsent(key, value, duration)`（`:116-141`）存在相同问题。

修复：`rawPutIfAbsent` 需先剔除过期条目（或在判断存在性时与 `expiry` 联动），保证「过期 == 不存在」在读写两端一致。

### 【中】4. `setTtl` 对「已过期但残留」的 key 会「复活」条目

`CacheEngine.setTtl` 在 `rawContains(key)` 为 false 的 else 分支仍调用 `store.rawSetTtl(key, duration)`（`CacheEngine.java:173-175`），而 `MemoryCache.rawSetTtl`：

```java
if (map.containsKey(key)) {            // 过期键仍物理存在 → 条件成立
    expiry.put(key, now() + duration.toMillis());   // 直接赋予新 TTL，复活
}
```

对「逻辑已删除」的过期键设置 TTL，会把它复活为有效条目，与「过期即移除」的整体模型相悖。真正不存在的 key 因 `map.containsKey` 为 false 被正确忽略（符合文档「实现自行决定」），但过期键的复活属非预期副作用。建议 `setTtl` 仅在 `rawContains` 为 true 时操作，else 分支直接忽略（不调用 `rawSetTtl`）。

### 【中】5. 无界模式下仍默认挂载并全量运行 W-TinyLFU

`MemoryCache(capacity<=0)` 仍 `new WTinyLfuEvictionPolicy<>(capacity, ...)`，且 Javadoc 称「W-TinyLFU 休眠、永不淘汰」。但：

- `selectEvictVictim` 在 `capacity<=0` 时返回 null（`ensureCapacity` 也提前返回）→ 确实不淘汰；
- 然而 `onPut/onGet/onTouch` 仍全量维护 `FrequencySketch` 三段 `LinkedHashMap` 与 `region` 索引，**每次读写都有非平凡开销**，并非「休眠」。

无界缓存（常见场景）为此白白付出 W-TinyLFU 全部维护成本。建议 `capacity<=0` 时不挂策略（或挂一个空操作策略），或在策略内对无界做短路。

### 【中】6. W-TinyLFU 的 `onPut` 会把「热点键」每次更新都重新打回 Window 段

`WTinyLfuEvictionPolicy.java:164-174`：

```java
public void onPut(K key, boolean existed) {
    sketch.increment(key);
    region.put(key, R_WINDOW);     // 无论 existed 与否，一律回到 window
    window.put(key, key);
}
```

引擎对更新键依次调用 `onPut` → `onTouch`：由于 `onPut` 先把键塞回 `R_WINDOW`/`window`，随后 `onTouch` 看到 `R_WINDOW` 只做 window 内 LRU touch，键最终停留在 window 而非原 protected 段。后果：**频繁更新的热点键会被反复降级到窗口**，削弱 SLRU 对热键的保护，降低 W-TinyLFU 应有的命中率。标准 W-TinyLFU 中更新已存在键通常只增频率、不重置所在段。建议 `onPut` 对 `existed==true` 仅 `sketch.increment`，不要重置 `region`/段位置。

### 【低】7. `cleanUp()` 返回值只含过期清理数，不含淘汰数

`CacheEngine.cleanUp()` 返回 `sweepExpired()` 的计数，但 `ensureCapacity()` 内还可能淘汰多条（`EVICT`）。文档称「返回被清理的数量」，实际少计了本次触发的淘汰数。建议累加 `ensureCapacity` 的淘汰计数一并返回。

### 【低】8. `LFUEvictionPolicy.selectEvictVictim` 为 O(n)，且在 `ensureCapacity` 的 while 循环中调用 → 最坏 O(n²)

`LFUEvictionPolicy.java:63-79` 每次遍历整个 `freq` 选最小，而 `CacheEngine.ensureCapacity` 在容量超限时会循环多次调用 `selectEvictVictim`。大容量 LFU 缓存写入热点时存在性能隐患。LRU/FIFO 为 O(1)，无此问题。建议 LFU 用最小堆或分段桶维护候选。

### 【低】9. `approxCount()` 把过期残留条目计入容量

`MemoryCache.rawCount()` 用 `map.mappingCount()`，包含已过期但未扫描清除的条目。因此 `isFull()` 与 `ensureCapacity` 的容量判断会把过期键算进来；虽 `ensureCapacity` 先 `sweepExpired()` 兜底，但 `isFull()` 可能短暂「虚满」。属软上限容忍范围内，但 `containsKey`（过期感知）与 `approxCount`（过期不感知）口径不一致，建议在文档中说明或让 `rawCount` 也剔除已过期键。

### 【低】10. `rawPutIfAbsent` 用 `== value` 判断插入成败

`MemoryCache.java:82-93`、`87-93` 用 `== value`（引用相等）判定是否插入。若调用方传入与既有值**同一引用**的对象，`computeIfAbsent` 不插入却返回 true，造成「假成功」。建议用布尔标记捕获映射函数是否真正执行，而非引用比较。

## 三、功能分工合理性

- **能力接口分层**（Cache → EvictableCache → BoundedCache，ObservableCache 横向 mixin）：合理、正交，便于按需组合。
- **RawStore SPI**：把「存储细节」与「缓存行为」解耦，MemoryCache/RemoteCache 各自适配，设计正确。
- **CacheEngine 作为编排核心**：所有行为逻辑集中于此，MemoryCache/RemoteCache 仅做委托，符合单一职责；但 `CacheEngine` 放在 `store` 包却不是「存储」，**包归属有误导性**，建议移入 `cache` 或 `cache.internal`。
- **RemoteCache 不实现 EvictableCache/BoundedCache**：正确——淘汰由服务端管理，本地不暴露相关能力，分工清晰。
- **事件聚合族（MUTATE/INVALIDATE）与具体事件对称触发**：设计一致、易用。
- **淘汰策略只决策不删存储**：`selectEvictVictim` 返回 key、由引擎删并派发事件，保证「删除语义唯一」（惰性过期/主动扫描共用 `expireKey`），这是本包最扎实的设计点。

## 四、功能与命名一致性

- `CacheEventListener` 文档称 `oldValue`/`newValue` 为 `Supplier`、`existed` 语义等，与代码一致（当前为 Supplier，历史 CompletableFuture 改动已被覆盖）。✅
- `CacheEventType` 文档与 `CacheEngine.fire` 调用点一致（INSERT/UPDATE/TOUCH→MUTATE，EVICT/EXPIRE/REMOVE→INVALIDATE，CLEAR 单列）。✅
- `setTtl` → 触发 `TOUCH`（文档定义为「TTL 被刷新」），一致。✅
- `EvictionPolicy` 文档详述 `onPut/onGet/onTouch/onRemove/...` 调用约定，与 `CacheEngine` 实际调用一致。✅
- **不一致点**：`MemoryCache` Javadoc 称无界时 W-TinyLFU「休眠」，实际仍全量运行（见问题 5）。
- **不一致点**：`BoundedCache.cleanUp` 文档「返回被清理的数量」与实现只返回过期数（见问题 7）。
- **命名小瑕疵**：`WTinyLfuEvictionPolicy` 字段 `region` 用 `ConcurrentHashMap` 但在锁外读取，虽 `ConcurrentHashMap` 提供可见性、功能正确，但与三段 `LinkedHashMap` 的加锁风格不统一，易误导维护者认为无需同步——建议加注释说明其无锁读是安全的。
- `RemoteCache` 的 `NO_EXPIRE = -1L`、`MemoryCache` 的 `IMMORTAL = 0L` 含义相同（永不过期标记）但命名与取值不同，跨实现未抽象为统一常量，可读性略差。

## 五、修复优先级建议

| 优先级 | 问题 | 类型 |
|--------|------|------|
| P0 | #1 UPDATE/MUTATE oldValue 取错值 | 逻辑/语义 |
| P0 | #2 ttl(不存在) 违反契约返回 ZERO | 契约 |
| P1 | #3 putIfAbsent 遇过期残留键不写入 | 逻辑 |
| P1 | #4 setTtl 复活过期键 | 逻辑/副作用 |
| P1 | #5 无界模式 W-TinyLFU 空转开销 | 性能/文档 |
| P1 | #6 W-TinyLFU onPut 重置热点键段 | 算法正确性 |
| P2 | #7 cleanUp 返回值缺淘汰数 | 文档/逻辑 |
| P2 | #8 LFU O(n²) | 性能 |
| P2 | #9 approxCount 含过期键 | 一致性 |
| P3 | #10 rawPutIfAbsent 引用相等判定 | 边界 |

建议先修 P0 两项（均有明确接口契约违背，且测试未覆盖，风险最高），P1 四项影响并发/性能正确性，P2/P3 可后续打磨。
