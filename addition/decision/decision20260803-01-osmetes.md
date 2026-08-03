# Decision: osmetes 扫描忽略策略（.gitignore 自动识别 + 可配置忽略规则）

日期：2026-08-03
模块：flora-osmetes / flora-osmetes-plugin

## 背景

osmetes 检查引擎原用 `Files.walk(root)` 扫描整个仓库根（默认 `${maven.multiModuleProjectDirectory}`），
不区分源码与非源码，把 `absent/`（2582 个 gitignored 三方 Java 文件）、`node_modules/`、
`target/` 中的 Tab/编码问题全部报成告警，造成大量无效噪音。

## 决策

1. **自研 gitignore 匹配器，而非引入 JGit 依赖。**
   flora-osmetes 是零外部依赖的纯 JDK 模块（module-info 无 requires），
   且 flora-sanctum 已有放弃 JGit 的先例。自研 `GitIgnore`（单文件解析+匹配）
   与 `GitIgnoreChain`（跨文件"深者覆盖浅者"组合）对齐 gitignore(5) 语义：
   取反、仅目录模式、锚定、`**`、字符类、转义、被排除目录内部不可重新包含。
   同时按 git 行为始终跳过 `.git` 目录。

2. **显式配置的忽略规则优先级最高。**
   Mojo 新增 `ignorePatterns` 参数（pom 中配置，`;` 分隔多条模式，语义与 gitignore
   一致），作为规则链顶层的 override 层，先于所有 `.gitignore` 求值。理由：
   用户显式配置应压过仓库自身的忽略规则。分隔符支持 `,;|&` 任意一个，多模式取并集。

## 影响

- `Osmetes.run` 改为 `Files.walkFileTree`，被忽略目录整棵剪枝（性能提升，避免误报）。
- Mojo `check` 新增 `ignorePatterns` 参数；flora-root 的 osmetes-check profile 配置
  `absent/;*.log`。
- 局限：未支持 `.git/info/exclude` 与 `core.excludesFile`（全局忽略），
  如未来需要可在 `GitIgnoreChain` 中追加低优先级层。
- 遗留：SecretCheck 对 `password`/`secret`/`token` 等变量赋值的误报（36 处）属既有问题，
  本次未处理。

## 追加决策：@SuppressWarnings("osmetes:<检查名>") 支持

**背景**：需要让源码显式豁免某类检查，注解按声明作用域（类/方法/语句）生效。

**决策**：自研轻量 Java 源码扫描器（`SuppressWarningsScanner`，零依赖），而非引入
JavaParser 等解析库。词法切分产出 `@SuppressWarnings` 注解，按花括号/分号/括号深度
确定作用域：类级=整个类型体、方法级=方法体、语句级=到分号、括号内（参数等）=仅
注解行。检查名由检查类自身 `name()` 定义，注解值 `osmetes:<name>` 与之匹配。
引擎仅对产生问题的 `.java` 文件解析并过滤行级问题。

**影响**：不引入新依赖；解析失败时静默跳过抑制（问题照常报告）；只支持标准
`@SuppressWarnings` 注解（非 `@SuppressWarning`）；当前不配置任何抑制规则，
既有 36 处 SecretCheck 误报保持原状。
