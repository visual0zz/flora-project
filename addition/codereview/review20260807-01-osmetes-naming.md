# review20260807-01-osmetes: 类/方法命名与职责分布审查

## 审查范围

`flora-osmetes` 模块全部主代码（14 个 `.java` 文件）。审查聚焦两点：

1. **命名**：类名/方法名/常量名是否准确反映职责、是否存在误导或重复。
2. **职责分布**：单一职责是否被遵守、是否存在过长方法、隐式依赖、关注点混杂。

整体结论：模块分层清晰（公开 API 包 `com.flora.osmetes` + 三个不导出子包 `check`/`gitignore`/`suppress`），SPI 扩展点设计合理。主要改进空间集中在 **引擎类 `Osmetes` 的职责过重** 与 **少量命名/常量重复**。

---

## 一、职责分布问题（优先级高）

### 1. `Osmetes.run(Path, List, String, Set, Map)` 过度臃肿（Osmetes.java:110-172，约 63 行）
核心编排方法把「配置下发 → gitignore 链构建 → 文件树遍历（内嵌匿名 `SimpleFileVisitor`，121-165 行，约 45 行）→ 扩展名分派 → 注解抑制 → 排序」全部塞在一个方法里，且匿名内部类进一步放大体量。违反单一职责。

**建议**：拆出 `configureChecks(checks, config)`、`createFileVisitor(absRoot, chain, active)`、`sortIssues(all)` 等具名方法；`SimpleFileVisitor` 抽为独立类或工厂方法，使 `run` 退化为"组装依赖 + 驱动遍历 + 返回结果"的薄编排层。

### 2. CLI 关注点混入引擎（Osmetes.java:54-74、246-284、287-289）
`main` 直接 `System.out`/`System.err` 输出、`print` 负责报告排版、`forceUtf8Output` 重置 `System.out`。引擎本应是纯计算（返回 `List<CheckIssue>`，已有 `run` 做到），却内置了输出与重定向。

**建议**（中等重构，需权衡）：把 CLI 入口抽到独立类（如 `OsmetesCli`），`Osmetes` 只保留计算与发现；`main` 仅做"解析参数 → 调引擎 → 调报告"。这样引擎可被测试/嵌入而无需碰 `System.out`。

### 3. `LineCheck` 与 `EncodingCheck` 的隐式顺序依赖（LineCheck.java:25-27）
`LineCheck.check` 在 UTF-8 解码失败时**静默 `return`**，注释明确"读取失败由 `encoding` 检查项负责报告"。这隐含了「`encoding` 必须先于 LineCheck 子类运行」的编排前提。若 `encoding` 被 `disabledChecks` 禁用，则所有解码失败文件的错误将**完全静默丢失、无任何提示**。属跨检查项职责边界不清。

**建议**：`LineCheck` 在解码失败时自行报告一条 `ERROR`（如 `无法以 UTF-8 读取`，与 encoding 检查的"不属于任何允许编码"互补）；或在 `run` 的禁用逻辑中对"被禁用却承担基础设施职责的检查项"给出警告。二者择一消除隐式耦合。

### 4. `SecretCheck` 体量偏大（SecretCheck.java，约 560 行）
单一类承担了「候选提取（`extractStringLiterals`/`extractBareScalars`）+ 判定链（`examine`/`looksLikeSecret`）+ 阈值配置解析（`applyInt`/`applyDouble`）+ 熵工具（`entropyDensity`/`printableRatio`/`alnumClasses`）+ 打码（`mask`）」等多组职责。熵相关工具（392-464 行）与已在用的 `com.flora.entropy.mesure.Entropy` 同域。

**建议**（可选，较大重构）：把 `entropyDensity`/`printableRatio` 下沉到 `com.flora.entropy` 模块，减少重复启发式；或至少把"判定链"显式化为 `Predicate<String>` 列表，提升 `examine` 可读性。当前功能正确，优先级低于前三项。

---

## 二、命名问题（优先级中）

### 5. `whitetail` 名实不符（WhitetailCheck.java:21，`name()` 返回 `"whitetail"`）
类名 `WhitetailCheck` 与中文注释"行尾空白检查项"、`checkLine` 实际做的"检测行尾多余空白"不符（`whitetail` 意为"白尾鹿"，概念应为 *trailing whitespace*）。名字与职责不一致，外部配置/报告里出现 `whitetail` 易误解。

**建议**：`TrailingWhitespaceCheck` / `name() = "trailing-whitespace"`（或沿用项目既定叫法，请确认是否故意）。属纯重命名，风险低。

### 6. 两个同名 `PREFIX` 常量语义不同（易混淆）
- `Osmetes.PREFIX = "[flora-osmetes]"`（Osmetes.java:46）—— CLI 输出前缀。
- `SuppressWarningsScanner.PREFIX = "osmetes:"`（SuppressWarningsScanner.java:29）—— 注解前缀。

同名、跨文件、作用完全不同。

**建议**：改名为 `CLI_OUTPUT_PREFIX` 与 `SUPPRESS_ANNOTATION_PREFIX`，消除阅读混淆。

---

## 三、常量/魔法值重复（优先级低，易修）

### 7. 分隔符正则 `"[,;|&]+"` 三处重复
- `Osmetes.parseNames` 内联 `names.split("[,;|&]+")`（Osmetes.java:213）
- `GitIgnore.parsePatterns` 内联 `patterns.split("[,;|&]+")`（GitIgnore.java:122）
- `EncodingCheck` 提为 `private static final String DELIMITERS`（EncodingCheck.java:46，引用处 65 行）

**建议**：统一为单一共享常量（如 `Osmetes.DELIMITERS` 或新建 `CheckConfig`/工具常量类），三处引用，避免改一处漏两处。

### 8. `.java` 后缀与 `"osmetes:"` 注解前缀散落
- `".java"` 在 `suppressByAnnotation`（`Osmetes.java:233`）、`SecretCheck.isBareScalarFile`（358-360 行）等处硬编码，无统一常量。
- `"osmetes:"` 仅在 `SuppressWarningsScanner` 定义为常量，但在 `Osmetes` 的 Javadoc/注释里以字面量描述，未引用同一常量。

**建议**：提取 `JAVA_EXTENSION = ".java"` 等小常量；`Osmetes` 注释引用 `SuppressWarningsScanner.SUPPRESS_ANNOTATION_PREFIX`。

### 9. `main` 与 `print` 重复统计 ERROR/WARN（Osmetes.java:69 与 :272）
同一 `issues.stream().filter(i -> i.severity() == Severity.ERROR).count()` 逻辑两处实现。

**建议**：抽 `countErrors(issues)` / `countWarnings(issues)` 工具方法复用。

---

## 四、已核实无问题（避免误改）

- **模块依赖**：`SecretCheck` 引用 `com.flora.entropy.mesure.Entropy`，而 `osmetes` 的 `module-info.java` 仅 `requires com.flora.root`。经查 `flora-root` 的 `module-info.java:66` 显式 `exports com.flora.entropy.mesure`，经 `requires com.flora.root` 合法可达，**不存在缺失的模块依赖**。
- **`EncodingCheck` 不继承 `LineCheck`**：因需按字节解码、扫描 C1 控制字符、并报告"读取失败"，刻意不继承，分工合理，无需调整。
- **`GitIgnore` 与 `GitIgnoreChain`**：单份解析 vs 目录层级叠加，职责划分清晰。

---

## 五、其它观察（非阻塞，仅供参考）

- `com.flora.entropy.mesure` 包名中的 `mesure` 为法语拼写（应为 `measure`）。属项目既定命名，跨模块改造成本高，仅记录不建议改。
- `GitIgnore.globToRegex`（~85 行，GitIgnore.java:200）与 `SuppressWarningsScanner.tokenize`（~64 行，:221）方法偏长但内聚，可酌情抽取内部辅助（`appendStar`/`skipLineComment` 等）提升可读性。
- `GitIgnore.Match` 返回 `Match` 枚举，`GitIgnoreChain.isIgnored` 返回 `boolean`（GitIgnore.java:142 vs GitIgnoreChain.java:75）：同名概念返回类型不一致，可接受，但可在 `isIgnored` 文档说明其基于 `Match` 映射。
- `parseNames` 为包级可见、主要供测试调用；作为公开 API 供 Maven 插件使用时建议 `public` 并补文档（当前 `OsmetesMojo` 实际调用的是 `Osmetes.parseNames`，包内可见即可，无碍）。

---

## 优先级排序（建议实施顺序）

1. **高**：#1 拆分 `Osmetes.run`（可独立提交，不影响行为）
2. **高**：#3 消除 `LineCheck`/`encoding` 隐式依赖（修正潜在静默丢错）
3. **中**：#5 重命名 `whitetail` → `trailing-whitespace`（纯重命名）
4. **中**：#2 抽取 CLI 入口（较大重构，单独评估）
5. **低**：#6/#7/#8/#9 常量与重复统计收敛
6. **可选**：#4 `SecretCheck` 熵工具下沉
