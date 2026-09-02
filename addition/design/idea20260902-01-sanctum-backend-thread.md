# sanctum 单线程后台执行器与轮询式自动锁定

日期：2026-09-02

## 背景与问题

原实现里导入、导出、远程同步、自动锁定分属不同线程，彼此不感知：

- 导入：`runImport` 用 `new Thread("import")` 裸线程跑（`SanctumGui`）。
- 自动锁定：`autoLockTimer`（`java.util.Timer` 守护线程）到点后在 EDT 上直接 `lock()`。
- 克隆导入 `doImportVault` 在 EDT 上同步跑 `RepoImporter.importRemote`（含网络，卡 UI）。
- 导出 `doExportWithDialog` 在 EDT 上同步跑 `exporter.exportTo`（解密全库 + 写文件）。
- 同步 `doSync` 在 EDT 上同步跑 `syncService.sync()`（含 git 网络 + close/重开）。

由此产生两个缺陷：

1. **导入中触发自动锁定**（无事务、竞态）：导入耗时超过 `lockTimeoutSeconds` 时，`autoLockTimer` 会在导入仍在跑时触发 `lock()`，清空 vault 密钥，导致导入抛异常、且可能留下半截数据。导入线程与锁定线程互不协调。
2. 多处后台/网络活直接在 EDT 上跑，卡 UI。

## 方案

引入**单线程后台执行器** `com.flora.sanctum.app.BackgroundExecutor`，所有后台活串行提交到它：

- **导入 / 导出 / 远程同步** 三类数据任务。
- **自动锁定判定** 也并入同一线程：后台线程循环「取任务 → 有则执行 → 无任务且仍解锁且空闲超时则锁定 → 否则睡眠 1 秒再查」。

把锁定判定收编进同一线程后，导入/导出/同步在执行期间循环卡在 `task.run()`，**根本不会走到超时判定那一行**，从而结构性消除「导入中锁定」——无需 suspend/resume 定时器式的线程交互。

### 循环语义

```
while (running) {
    Task t = queue.poll();
    if (t != null) { runTask(t); continue; }      // 有任务：执行期间不判定锁定
    if (unlocked.getAsBoolean()) {                  // 仅仍解锁才判定（已锁定只睡眠待命）
        if (now - lastActivity > idleTimeout) { lockNow.run(); markActive(); continue; }
    }
    sleep(1000);
}
```

### 约定（拍板细节）

1. **锁之后循环**：判定前先 `sanctum.isUnlocked()`；已锁定则跳过判定、只睡眠待命；解锁成功后（UI 或解锁任务）`markActive()` 重置计时。并给 `lock()` 加幂等保护（`sanctum == null` 直接返回），避免轮询窗口内重复触发。
2. **任务完成续命**：提交任务与任务结束都 `markActive()`，即从「刚提交 / 刚做完」时刻起重算空闲阈值；长任务期间不锁，做完后从完成时刻起算。
3. `lock()` 由后台线程经 `SwingUtilities.invokeLater` 切回 EDT 执行（原 `lock()` 本就在 EDT 跑，内部只改 UI/停定时器，安全）。
4. 复用现有约 40 处 `resetAutoLock()` 调用点，仅将其实现从「重新 schedule Timer」改为 `executor.markActive()`，调用点一行不动。
5. 任务执行前自检 `sanctum.isUnlocked()`（单线程串行后任务可能排很久才执行，状态可能已变）。
6. `runTask` 包 try/catch，异常经 `Listener.onFailure` 在 EDT 报告，避免任务抛异常终止整个循环/线程。
7. 退出：`volatile running` 标志 + 守护线程，退出时 `shutdown()` 置 false 并 interrupt。

## 任务进行中 UI 报告

`BackgroundExecutor.Listener` 回调在 EDT 上触发：

- `onStart(name)`：状态栏显示「正在{name}…」+ 显示转圈图标（复用 `SpinnerIcon`）。
- `onEnd(name)` / `onFailure(name, t)`：隐藏转圈。

转圈图标 `taskSpinnerLabel` 加入主界面工具栏（与 `statusLabel` 并列），默认隐藏，由 `startTaskSpinner()` / `stopTaskSpinner()` 驱动（50ms 定时器更新角度）。

## 改动文件

- 新增 `flora-sanctum-app/.../app/BackgroundExecutor.java`：单线程执行器 + `Task` / `Listener` 接口。
- `flora-sanctum-app/.../app/ui/SanctumGui.java`：
  - 删除 `autoLockTimer` 字段与 `startAutoLockTimer()`；`stopTimers()` 仅保留 `clipboardTimer`。
  - `resetAutoLock()` → `executor.markActive()`。
  - `onUnlocked` 末 `startAutoLockTimer()` → `executor.markActive()`。
  - `bootstrap()` 初始化 `executor`（`initBackgroundExecutor()`）。
  - `runImport` / `doExportWithDialog` / `doSync` / `doImportVault` 改为提交任务到 `executor`，原 EDT 同步的写盘/网络逻辑移入任务体（任务内保留原有弹窗 + `invokeLater`）。
  - `lock()` 加幂等保护。
  - 主界面工具栏加 `taskSpinnerLabel`，新增 `startTaskSpinner()` / `stopTaskSpinner()`。
