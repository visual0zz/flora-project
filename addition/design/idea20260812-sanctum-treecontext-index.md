# idea20260812-sanctum-treecontext-index

## 背景（问题）

改造前，`TreeContext` 仅以 `Map<UUID,JsonObject> objects` + `Map<UUID,Block> blocks` 两张 uuid 索引表
在内存中维护对象图。`childrenOf(parent)`（按 parent 列出直接子节点）是**全图线性扫描**，
每次遍历整个对象图匹配 `parent` 字段。条目/组的子节点查询（预设字段、
自定义字段、子组、子条目）都走它，规模增长后查询成本随对象总数线性上升。

此外模型层**无任何并发保护**：`objects`/`blocks`/底层 `MarkdownObjectStore` 都是非线程安全结构，
`write` 内 `nextTimestamp()` 先做全库 `scan()` 求最大时间戳、再 `store.put()`，两段之间没有原子性。
虽然当前 GUI 全程在 Swing EDT 单线程写，但模型层不应假设调用方单线程；`SyncService` 在后台线程
重开后重建会话，未来任何并发写都会触发：时间戳碰撞、索引与存储不一致、迭代期 `ConcurrentModificationException`。

## 设计

### 1. 统一双索引

在 `TreeContext` 内新增两张索引，与 `objects`/`blocks` 同步维护：

- `parentOf: Map<UUID,UUID>` —— uuid → 父对象 uuid（顶层/根概念 parent 为 null）。
- `childrenByParent: Map<UUID, List<UUID>>` —— 父 uuid → 直接子 uuid 列表（插入序）。

`childrenOf(UUID parent)` 改为 O(1) 查表返回列表副本；新增 `parentUuidOf(UUID)` 走 `parentOf`。
索引在 `scanAll()` 构建（从每对象的 `parent` 字段解析），在 `writeCipherBlock`/`delete` 增量维护。

`indexObject` 必须是**幂等**的：写入前先清除该 uuid 在旧父（或同父旧位置）的索引，仅当目标父下
不含该 uuid 时才追加，从而同时覆盖「同父重复」与「parent 变化（移动）」两种情形。这是在 KeePassXC
导入（setIcon/rename 以同 uuid 二次写入）后消除重复渲染的根因修复，详见
`decision/decision20260830-02-sanctum.md`。

注意：调用方传入 `childrenOf` 的 parent 永远是可解析的 real UUID（条目/组 uuid；顶层项的 parent
是根对象 uuid，亦为 real UUID），故索引以 `UUID` 为键即可覆盖全部现有用例，无需支持根概念 tag 字符串键。

### 2. 写入线程安全

引入单一 `ReentrantLock` 守护**所有**触碰 `objects`/`blocks`/两张索引/底层 store 的方法：
`scanAll`、`write`、`writeWithDek`、`writeCipherBlock`、`delete`、`blockOf`（惰性补扫会写 `blocks`）、
以及读方法 `read`/`childrenOf`/`parentUuidOf`/`parentGroupUuid`/`objects()`。

关键：锁必须覆盖 `nextTimestamp()`（全库 scan 求 max）与 `store.put()` 的**整段**，否则并发写会
读到相同的 max 时间戳产生碰撞、或 store 写互相交错。节点写操作彼此不嵌套（不存在 `write` 内再调
`write` 的路径），但用 `ReentrantLock` 仍允许未来嵌套且不阻塞。

`MarkdownObjectStore` 自身保持非线程安全（单文件原子替换已防掉电半写）；并发串行化交由 `TreeContext`
的锁统一负责，避免两层锁的复杂度。

### 3. 一致性保证

- 同一锁下完成"内存图更新 + 索引更新 + 落盘"，三者要么全成要么全不成（落盘抛异常时内存图已先改，
  但 store 幂等覆盖、重扫即可恢复，与原实现一致）。
- `delete` 在锁内同步移除 `objects`/`blocks`/两张索引，避免读到已删节点的孤儿索引。

## 验证

- 现有 core/app 测试全绿（均为单线程顺序读写，加锁后行为不变）。
- 新增并发冒烟测试：多线程对同一 `TreeContext` 交替 `createEntry`/`writePreset`/`delete`，
  校验无 `ConcurrentModificationException`、无时间戳碰撞、索引与 `objects` 始终一致。
