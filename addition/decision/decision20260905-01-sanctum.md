# 决策：KDBX 内置图标按 IconID 精确映射（超范围取模回落）

日期：2026-09-05
模块：flora-sanctum（core：icon / io.importer.kdbx）

## 背景

预制图标库此前是一批无编号语义的 SVG（按名称字母序使用），KDBX 导入内置图标时走
`KdbxMapper.resolveIcon` 的 `iconId % 库大小` 有损取模：KeePass 的 `IconID=3`（服务器）
落到 sanctum 库里只是字母序第 4 个图标，与 KeePass 的原图含义毫无关系。

用户重建了整个预制图标库，按 KeePass 2.x 的 `PwIcon` 枚举（ID 0–68，共 69 个）重绘，
并要求文件以 `编号-含义` 命名（`01-earth.svg`、`22-file.svg`、`48-folder.svg`）。
用户进一步要求：导入时按编号精确映射，编号超出库范围时取模。

## 决策

1. **IconID 的真值来源是文件名前缀，而非硬编码对照表。**
   图标文件名形如 `NN-含义.svg`，`NN` 即 KeePass 的 IconID。`BuiltinIcons` 解析前导数字
   构建 `IconID → 图标名` 映射；导入时先精确命中，命中失败再对库大小取模回落。

2. **保留 `names()` 的字母序，另设 ID 序查找。**
   `names()` 继续按字母序返回，供 GUI 图标选择器展示；新增 `nameForIconId(int)` 专供
   KDBX 导入使用。两者职责分离，互不干扰。

3. **无数字前缀的图标排在取模序列末尾。**
   取模索引落在按 IconID 排序的列表上。无前缀图标的排序键取 `Integer.MAX_VALUE`（而非 `-1`），
   确保它们排在末尾，不会抢占 0 号附近的位置。

## 为什么不用硬编码对照表

- 对照表会与资源目录脱节：加了图标忘了改表、或删了图标表里留残项，都会静默错配。
- 文件名前缀把「编号 → 图标」的对应关系放在图标自己身上，增删文件即自动生效，无第二处需同步。
- 代价是 `BuiltinIcons` 与 `NN-` 命名约定耦合：新增无编号图标仍可用，只是不参与精确映射。

## 影响

- `BuiltinIcons`：新增 `nameForIconId(int)`、`ORDERED_BY_ID`、`ID_TO_NAME`、`sortKey()`。
- `KdbxMapper.resolveIcon`：内置图标分支改为 `BuiltinIcons.nameForIconId(iconId)`。
- 新增测试 `BuiltinIconsIconIdMappingTest`（4 项）：范围内精确命中、0–68 逐号命中、
  超范围回绕取模、同编号映射稳定。

## 踩到的坑

Maven 的 resources 插件**不会清除 target 里已删除的资源**。用户删掉的旧图标仍留在
`target/classes/icons/library/`（107 个 vs 源目录 69 个），导致测试读到陈旧集合。
删除图标后需 `mvn clean`（或手工删 `target/classes/icons`）才能让建构产物与源目录一致。

---

# 决策（续）：新建库写入时间戳锚点

日期：2026-09-05
模块：flora-sanctum（core：model.vault）

## 背景

`SanctumTest.entryHasBuiltinPasswordUrlUsernameLabels` 断言新建条目 `createTime` 落在
`before..after` 之间，此前**确定性失败**：`ct ≈ 1971`（即 `1970 + 1 年`）。

根因有两层：

1. **新建库初始块打在哨兵时间戳 `1` 上。**
   `VaultCreator.writeManifestBlock` 把 `"1"`（实为块级时间戳，版本在负载内另存）、
   `writeRootGroup` 把 `writeCipherBlock(..., 1)` 都写成时间戳 `1`。于是全库块最大时间戳
   为 `1`，`VaultUnlocker.maxBlockTimestamp` 算出 `min(1 + 1年, now) = 1971` 作为时钟锚点。

2. **锚点与单调基准错位 + 纳秒→毫秒向下取整。**
   原 `maxBlockTimestamp` 在解锁早期（第 64 行）采样墙钟作为锚点，而 `WarehouseClock.startNanos`
   在紧随其后的构造里才采样；二者相差解锁收尾的耗时。`sessionElapsedMillis` 又用**向下取整**，
   使 `ct = 锚点 + floor(经过)` 可能比调用方在解锁后采样的 `before` 还小 1ms，导致同一毫秒内
   的竞态失败（单独跑 SanctumTest 偶过、与其他类同跑必败）。

## 决策

1. **新建库初始块打真实当前时间戳。**
   `VaultCreator` 在 `create` 内取一次 `System.currentTimeMillis()`，manifest 块与 root 对象块
   均使用它，不再写哨兵 `1`。旧版已落盘（时间戳为 `1`）的库也一并受益见下条。

2. **锚点计算与 `startNanos` 同源，并放宽到「当前毫秒」。**
   锚点语义改为 `max(全库块时间戳上限, 当前毫秒)`，且封顶 `当前毫秒 + 1 年`（防异常巨大块时间戳
   无限制前移）。该计算移入 `WarehouseClock` 构造，与 `startNanos` 在同一时刻采样，消除两者错位。
   旧库的 `1` 哨兵因此被 `max(1, now) = now` 自然抬到真实当前时间。

3. **会话偏移向上取整。**
   `sessionElapsedMillis` 改用 `Math.ceilDiv(纳秒差, 1_000_000)`，保证
   `ct = 锚点 + ceil(经过) ≥ 写入时刻墙钟 ≥ 调用方 `before``，彻底消除同毫秒竞态，同时不破坏
   会话内单调递增。

## 为什么这样改

- 不改「锚点 = max(块上限, 当前时间)」的设计本意，仅修正哨兵与采样错位这两个具体缺陷。
- 把锚点抬到 `now` 而非历史上「`max + 1 年`」的写法，对休眠超过一年的库更自然（锚点 = 真实当前），
  且仍满足跨会话单调递增：本会话写入 `≥ now ≥ 旧块时间戳`。
- 向上取整相较向下取整只至多偏移 1ms，且令写入时间戳恒不低于同毫秒墙钟采样，是消除竞态的最小改动。

## 影响

- `VaultCreator`：`writeManifestBlock` / `writeRootGroup` 接收并使用真实 `created` 时间戳。
- `VaultUnlocker.maxBlockTimestamp`：仅返回块时间戳上限（锚点封顶逻辑移至 `WarehouseClock`）。
- `WarehouseClock`：构造期同源采样 `now` 计算 `baseTimestamp`，偏移 `ceilDiv` 向上取整。
- `SanctumTest.entryHasBuiltinPasswordUrlUsernameLabels` 修复并通过（core 全量 103 项绿）。

