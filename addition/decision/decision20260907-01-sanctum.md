# 决策：设置页新增「主密码与 Argon2 参数」同页修改

日期：2026-09-07
模块：flora-sanctum（app：ui / core：model,crypto）

## 背景

此前换主密码仅存在于 core（`MasterKeyRotator`），未被任何 UI 调用；Argon2 参数
只能在新建库时通过 `NewVaultDialog` 的高级选项指定，一旦建库便无法再调整。
需求：在仓库设置页提供「修改主密码 + 调整 Argon2 参数」的能力——两者放在同一页、
一次保存；并附一个「测试」按钮，按当前内存/并行度实测单次迭代耗时，估算总耗时最接近
1 秒的严格整数迭代数，以括号形式追加在迭代输入框右侧，**不直接改输入值**。

## 决策

1. **设置页「仓库设置」首条新增复合表单条目（VAULT 伪对象条目）。**
   设置页中栏是「键值设置 / 仓库对象」二分的条目列表，右栏按条目类型渲染。该功能是一整块
   多控件表单（密码×2 + 参数×3 + 测试 + 保存），不适用单控件行模型，故仿 SSH 密钥/远程对象
   的做法：在 VAULT 分类下放一个 `ObjectEntry("master-kdf")`，右栏整页渲染 `MasterKdfPanel`。
   密码与参数同面板、单一「保存」一次提交。

2. **保存 = core 轮换 + 同实例重建会话。**
   换密码后根对象 uuid（`RootUuid.derive(KEK)`）与磁盘布局改变，内存树仍指向旧根 uuid，
   必须重建会话。流程：`changeMasterPassword` → `lock()` → `unlock(新密码)`（复用既有
   Argon2 派生，与测试中的 close→reopen 语义一致），再 `onUnlocked` 刷新整个主界面。
   失败时磁盘可能已部分迁移，直接回到该仓库解锁页让用户重新校验，不在不确定状态上停留。

3. **测试按钮：两步实测，取最接近 1 秒的整数迭代数作为括号提示。**
   先用「内存、并行度」输入、迭代=1 跑与解锁相同的 `Argon2KDF.derive` 路径，得到 t1：
   - t1 ≥ 0.5s：单次计时已足够可信，直接采用；
   - t1 < 0.5s：计时噪声大，先按 t1 估出总耗时约 1 秒的迭代数 n0，再实测「n0 次迭代」档
     的总耗时 T(n0)，用 T(n0)/n0 折算更可信的单次耗时。
   之后取正整数 n 使 `|n·单次耗时 − 1|` 最小（浮点等距容差内取较小 n），提示文案形如
   `(n 次 ≈ x.xx 秒)`；建议只作参考，不写入迭代字段。实测任务不落公共 ForkJoin 池，而是
   走应用 `BackgroundExecutor`：单线程串行、任务执行期间不判定自动锁定，与导入/同步等后台
   任务一致排队。

4. **core 只增两个公开只读口子。**
   - `Sanctum.KdfParams` + `Sanctum.kdfParams()`：读当前 manifest 参数供表单预填。
   - `Argon2KDF.suggestIterationsForOneSecond(double)`：纯算术建议函数，独立于 UI 可测。

## 为什么不用模态对话框 / 独立页面

- 设置页既有三栏结构与「对象详情渲染」机制已成熟，复用成本最低、入口与其它仓库设置一致。
- 保存过程中的模态进度框沿用了 `createAndUnlockVault` 既有模式（未解锁禁止重入）。

## 影响

- core：`Sanctum.kdfParams()` / `KdfParams`；`Argon2KDF.suggestIterationsForOneSecond`。
- app：新增 `ui/MasterKdfPanel.java`；`SanctumGui` 在 VAULT 分类注册伪对象条目并实现
  `renderMasterKdfPanel` 与 `changeMasterPasswordAndReload`。
- 测试：`Argon2IterationHintTest`（纯算术 3 项）；`SanctumTest.kdfParamsReflectsCurrentManifest`。
- 行为：换密码/调参后回到主界面（等价一次重新解锁），不留在设置页。

## 决策（补充）：估算逻辑抽为共享探针并接入新建仓库对话框

同一估算能力在「新建仓库」对话框也应可用。将两步实测与后台调度抽为
`ui/Argon2IterationProbe`（提交至 `BackgroundExecutor`、回调 EDT、含提示文案与实测函数），
`MasterKdfPanel` 与 `KdfParamsPanel`（新建仓库「高级 → Argon2id」框）都只做参数校验与
按钮/标签的本地 UI 态，不再各自实现测量算法。`NewVaultDialog` 构造改为注入
`BackgroundExecutor` 并透传给 `KdfParamsPanel`。
