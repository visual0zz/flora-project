---
name: 缓存接口分层细化（EvictionConfigable 轴分离）
subject: 把「可挂策略」从「有界」中拆出为独立接口，并把 AbstractCacheStore 拆为 Bounded/Remote 两个基类
date: 2026-07-25
module: flora-root (com.flora.cache)
---

## 决策背景

`decision20260725-03` 把缓存抽象定为三层线性链
`CacheStore → ObservableCacheStore → BoundedCacheStore`。但 `BoundedCacheStore extends ObservableCacheStore`
隐含「有界 ⇒ 必然可观测」，而「可挂淘汰策略」与「有容量约束」本是正交的两件事；且「策略挂载」
被焊死在有界这一层，无法表达「无界但挂了策略（仅统计/准入）」的合法类型。

用户提出细化方案（2026-07-25 会话）：
1. 新增 `EvictionConfigableCacheStore`（继承 `CacheStore`）表达「能配置驱逐策略」这一独立能力；
2. `BoundedCacheStore` 改为继承 `EvictionConfigableCacheStore`（**不再**继承 `ObservableCacheStore`）；
3. 原 `AbstractCacheStore` 改名 `AbstractBoundedCacheStore`，作为有界类的基类；
4. 新增 `AbstractRemoteCache`，`RemoteCache` 继承它（远程不实现 `BoundedCacheStore`）。

用户拍板：**A=保留监听**；**B2=远程只实现 `CacheStore + ObservableCacheStore + EvictionConfigableCacheStore`（不实现 `BoundedCacheStore`）**；**C=闸门由我定**。

## 落地方案

- 新增 `com.flora.cache.EvictionConfigableCacheStore`（extends `CacheStore`）：声明 `setEvictionPolicy` / `evictionPolicy`。
- `BoundedCacheStore` 改 `extends EvictionConfigableCacheStore`（移除 `ObservableCacheStore` 父类），保留 `gc/isFull/capacity`。
- `ObservableCacheStore` 维持 `extends CacheStore`，与有界轴成为**兄弟**关系。
- 抽象基类拆分（与用户原方案的一处偏差，见下）：
  - 保留 `AbstractCacheStore` 作为**共享引擎**（实现 `CacheStore + ObservableCacheStore + EvictionConfigableCacheStore`），
    承载全部 put/get/remove/fire 逻辑与 `rawXxx` 钩子，避免两份 ~250 行引擎代码重复。
  - `AbstractBoundedCacheStore extends AbstractCacheStore implements BoundedCacheStore`：仅负责把「容量」维度显式兑现为有界契约。
  - `AbstractRemoteCache extends AbstractCacheStore`（**不**实现 `BoundedCacheStore`）：承载 namespace/wrapKey/doXxx 钩子/rawXxx 映射/setEvictionPolicy 空实现。
  - `RemoteCache extends AbstractRemoteCache`：瘦子类，保留 `RemoteCache(String)` 构造器供匿名子类使用。
- `MemoryCache` 改为 `extends AbstractBoundedCacheStore`，行为不变。

## C 闸门决策（由我定）

`onPut/onAccess/onRemove` 的唤醒闸门由 `p != null && capacity > 0` 放宽为 **`p != null`**。
理由：既然 `EvictionConfigableCacheStore` 已提升为独立轴，「无界但挂了策略」即应为合法语义——策略照常
收到读写通知（统计/准入），但 `EvictionPolicy.evict()` 内部因 `capacity <= 0` 返回 `null`，故不触发删除。
这把原先 `capacity > 0` 表达的运行时节流，提升成了类型系统的正交语义；远程缓存 `setEvictionPolicy`
为空操作（策略恒为 null），故不受此闸门影响，行为不变。

## 与用户原方案的一处偏差（已主动决定并说明）

用户要求「`AbstractCacheStore` 改名 `AbstractBoundedCacheStore`」。为避免在 `AbstractBoundedCacheStore`
与 `AbstractRemoteCache` 间复制整份 put/get/remove/fire 引擎，我保留了 `AbstractCacheStore`
作为共享引擎，让 `AbstractBoundedCacheStore` 与 `AbstractRemoteCache` 都继承它。这更贴合用户「瘦分层、避免堆叠/重复」
的偏好，且类型契约与用户意图完全一致（有界基类 + 独立的非有界远程基类）。如坚持字面改全名，可再调整。

## 验证

`flora-root` 全量 **1180** 测试通过（含缓存用例 21）。无破坏性改动，外部仅 `MemoryCache` 被 `ConverterRegistry`
引用，类型不变。

## 涉及文件

- 新增：`EvictionConfigableCacheStore.java`、`AbstractBoundedCacheStore.java`、`AbstractRemoteCache.java`
- 改：`BoundedCacheStore.java`、`AbstractCacheStore.java`（去 Bounded 父类 + 闸门放宽）、`MemoryCache.java`（父类改）、`RemoteCache.java`（瘦子类）
- 不变：`CacheStore`、`ObservableCacheStore`、`EvictionPolicy`、`WTinyLfuEvictionPolicy`
