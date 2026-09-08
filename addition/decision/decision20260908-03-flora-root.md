# 决策：mock 包（jsonschema / regex）正确性与结构治理

日期：2026-09-08
模块：flora-root（mock）

## 背景

移除长度预算后对 mock 包做了一次全面体检，用一次性探针实测确认了 6 个行为缺陷：
DFA 子集构造在字符区间重叠时丢解、`uniqueItems` 值空间不足时空转、
`minProperties` 补空串产出非法实例、不支持的正则炸掉整份生成、
`format=uuid` 破坏同种子可复现、非 BMP 字符生成错串。
此外 `GenerationNode` 已膨胀到 620 行，正则每次生成都重新编译。

用户要求：不考虑兼容，只按"逻辑上最自然"的形态改。

## 决策

### 1. 自动机以"结构保证匹配"为准绳

- **子集构造**：同一子集上的字符集合先按端点切成原子区间，每个区间取全部落入其中的
  转移目标的 ε-闭包。原先按 CharSet 分组 + `refineTransitions` 取"第一个相交的"会覆盖
  重叠区间（`[a-z]` 吃掉 `[c-f]` 分支）。`refineTransitions` 随之删除。
- **全集**：`CharSet` 从 BMP 扩到全部码点（0x10FFFF），并剔除代理区——孤立代理项无法
  序列化为合法文本。`Automaton.matches` 按码点推进，`RegexCompiler` 按码点解析。
- **规模闸门**：DFA 状态上限 4096、乘积状态上限 4096，超出抛 `AutomatonException`
  （由上层降级），避免指数膨胀耗尽内存。
- **语义对齐 JDK**：`\s` 补上垂直制表符 0x0B；`.` 只排除 5 种行终止符；
  `\b`/`\Q`/内联标志/悬空量词等"不支持"的语法显式抛异常，不再静默当字面量。
- **`a*` 的零次重复**：无界重复的出口补上 ε 边，空串终于可达。
- **删除 `estimateLength()`**：它用 `Math.random()` 破坏可复现，且随预算一起失去用途；
  改为确定性的 `minLength()`（最短串长度）。

### 2. 生成策略：硬约束优先，不支持即降级

- `uniqueItems`：单位置有限重试（32 次）；值空间耗尽时，未满足 `minItems` 则放弃唯一性，
  否则截断长度——不再空转。
- `minProperties`：按 `additionalProperties` 的子 schema 补值，而不是塞空串。
- `patternProperties`：属性名用 `RegexStringGenerator` 按该 pattern 生成（原先是随机 4 字母）。
- **正则不受支持时不中断生成**：`StringGenerator` 捕获 `AutomatonException` 降级为
  长度区间内的随机串。这是唯一无法保证满足 `pattern` 的情形，已在 `JsonGenerator` 文档标注。
- `multipleOf`：全程 `BigDecimal`（不经过 double），取值方式改为"在 `[min,max]` 内的
  合法倍数中随机挑一个"，而非先随机再截断（后者会落到区间外）。
- 无 `multipleOf` 的 number 限制小数位 ≤ 2，避免 20 位小数的怪值。

### 3. 结构：按类型拆分 + 编译期收集正则

- `GenerationNode` 只做分派（const/enum/$ref/anyOf/oneOf/if/type），
  生成逻辑拆到 `ObjectGenerator` / `ArrayGenerator` / `StringGenerator` /
  `NumberGenerator` / `MinimalInstance`，公共取值与类型推断抽到 `Nodes`。
- 正则在编译期收集进 `GenerationNode.patterns`（allOf 各分支 + 自身），
  废弃写回 schema 的 `_patterns` 内部键——用户 schema 的键空间不被实现污染。
- 交集自动机在节点上惰性构造并缓存；`RegexStringGenerator` 也缓存自己的自动机。
- 递归截断层的 string 也按正则采样，截断层仍满足 `pattern`。

### 4. 语义取值单一来源

- `FormatGenerator` 成为 format 与语义值的唯一取值源（日期/时间/邮箱/URL/域名/IP/UUID），
  `SemanticStringGenerator` 全部委托，杜绝两套实现漂移。
- `uuid` 由注入熵源构造，不再用 `UUID.randomUUID()`。
- 推断顺序改为**规则优先于分词位置**：`userEmail` → EMAIL（原先被 USERNAME 抢走）。
- 整名包含兜底要求关键词长度 ≥ 4，`no`/`day` 这类短词只在整词命中时生效。

### 5. 测试

- 新增 `JsonGeneratorPropertyTest`：21 个覆盖各关键字的 schema × 30 个固定种子，
  断言生成结果通过 `JsonSchema` 校验且 5 秒内完成（能自动抓"非法实例"与"空转"）。
- 新增 `SemanticStringGeneratorTest`（推断规则、取值形态、uuid 可复现）。
- `AutomatonTest` 补 8 个边界用例（重叠分支、增补平面、零次重复、行终止符、状态上限等）。
- `allOfPatternsIntersect` 改用 `find()` 断言：JSON Schema 的 `pattern` 是查找语义，
  原先的 `matches()` 比规范更严，误判了合法实例。

## 影响

- 新增：`ObjectGenerator`、`ArrayGenerator`、`StringGenerator`、`NumberGenerator`、
  `MinimalInstance`、`Nodes`；删除：`LengthEstimator`。
- 公开 API 变化：`Automaton.estimateLength()` 移除，新增 `Automaton.minLength()`；
  `RegexStringGenerator.estimateLength(String)` 移除，新增实例方法 `minLength()`。
- 验证：mock 相关 102 项全部通过；flora-root 全量 2121 项中仅
  `LogTest.testGlobalRetentionAcrossDays` 失败（既有日期边界问题，与本次无关）。
