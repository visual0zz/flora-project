# 决策：KeePassXC 导入后组/条目重复渲染的修复方案

日期：2026-08-30
模块：flora-sanctum-core（TreeContext）

## 现象

导入 KeePassXC 文件后，几乎每个组与条目在左侧组树/条目列表中重复出现多次；
删除某个 item 或锁定后重新解锁则恢复正常。属显示问题，数据文件本身干净。

## 候选方案

- 候选 A：在 UI 刷新路径（rebuildGroupTree / refreshEntryList）做去重。
- 候选 B（用户初选）：导入写入临时树，完成后在 EDT 一次性原子替换进 objectTree() 并重建，
  以消除并发读写活跃模型与任何重复触发隐患。
- 候选 C（最终采用）：修复根因——`TreeContext.indexObject` 非幂等。

## 决策

采用候选 C，未采用候选 B。

**Why：**
经排查，真正触发点是 `KdbxMapper` 在 `createChildGroup`/`createEntry` 之后对带图标的
节点调用 `setIcon`，而 `setIcon`/`rename` 会以**同一个 uuid** 再次 `ctx().write(...)`，
再次进入 `indexObject`。彼时 `indexObject` 不具备幂等性（仅向父组子列表追加、不清除旧位置），
同一 uuid 被追加两次；`addGroupNode`/`childGroups()` 经 `childrenOf()` 遍历该含重复项的
列表，导致节点被渲染两次。KeePassXC 文件几乎每个节点都带图标，故"每个 item 都被重复"。
重锁后 `unlock` 经 `scanAll()` 从文件重建索引（每 uuid 仅一次）所以恢复正常——反证数据无
问题、问题在内存索引。`indexObject` 的幂等约定见 `../design/idea20260812-sanctum-treecontext-index.md`。

候选 B 的“原子替换”即便实现，临时树仍走同一条 `createChildGroup`+`setIcon` 写入路径，
索引同样会重复；除非把“替换”实现成“从干净文件整体重建索引”，那等于变相重解锁，
代价大于针对性修复。同理 `rename()` 复用同 uuid 重写也存在同一潜在重复（重命名后会重复），
候选 C 一并根治。

**How to apply：**
- 任何以相同 uuid 二次写入对象（setIcon / rename / 移动）的代码，都依赖 `indexObject` 的幂等性，
  新增此类写入路径时无需再手动去重（幂等约定见上述设计文档）。
- 若未来确需"导入期间模型完全不反映中间态"，再单独评估候选 B 的临时树隔离，与本次根因修复互不冲突。
