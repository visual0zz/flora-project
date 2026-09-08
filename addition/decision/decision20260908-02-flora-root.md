# 决策：JsonGenerator 改为语义优先生成 + 深度概率展开，移除长度预算

日期：2026-09-08
模块：flora-root（mock.jsonschema）

## 背景

原 `JsonGenerator` 以**推荐长度（targetLength）预算**驱动一切：对象属性按估算权重瓜分预算、
数组长度 = 预算/元素估算、递归由预算 sigmoid 概率决定是否展开、字符串长度 clamp 到预算。
问题是输出"像随机字符"而不像真实数据，且规模控制耦合在长度估算这种间接量上。

新策略（用户指定思路）：
1. 字符串字段按**字段名猜测含义**生成语义化值，校验是否满足该字段的正则；
   连续 5 次被拒则放弃语义，改用对应正则调 `RegexStringGenerator` 生成。
2. **可选字段**用随深度递减的概率决定是否继续展开一层。
3. **不再使用长度预算驱动**。

## 决策

### 1. 语义优先 + 拒绝回退（新增 `SemanticStringGenerator`）

- 推断：`tokenize`（camelCase/PascalCase/非字母数字分词、统一小写）→ 先按 token 精确命中规则，
  再按整名 `contains` 兜底；规则按"更具体在前"排序（`username` 先于 `name`）。
  覆盖 ~36 类语义：姓名/用户名/密码/邮箱/手机/电话/URL/域名/IP/UUID/ID/
  日期/日期时间/时间/地址/城市/国家/公司/标题/描述/状态/类别/标签/币种/价格/
  年龄/邮编/编码/性别/颜色/语言/版本/路径/端口。
- 取值：按语义生成"像样"的值（如 `phone` → `1[35789]xxxxxxxxx`），
  uuid 由注入熵源构造而非 `UUID.randomUUID()`，保证同种子可复现。
- 合规判定：全部 pattern 命中（走 JDK `java.util.regex` 的 `find()`，与校验侧一致；
  正则无法编译视为不合规）+ 长度落在 `[minLength, maxLength]`。
- 回退：`SemanticStringGenerator.MAX_REJECTIONS = 5` 次被拒后，
  单正则 → `RegexStringGenerator.of(pattern).generate(target)`；
  多正则（allOf 合并）→ 自动机交集 `combined.sample(target)`；
  无正则 → 按长度区间造随机串。

### 2. 可选部分的展开概率随深度递减

`GenerationContext.expandProbability() = max(0.02, 0.9 * 0.7^depth)`：
- 深度 0 → 0.9，深度 1 → 0.63，深度 3 → 0.31，深度 6 → 0.10，深度 ≥ 12 触底 0.02。
- 应用点：非 required 属性、patternProperties、additionalProperties（1..2 个）、
  `$ref` 循环引用的继续展开。必填/硬约束（required/minItems/minProperties）不受概率影响。
- `HARD_DEPTH_LIMIT = 1000` 保留为纯防溢出保险（正常由概率收敛）。

### 3. 移除长度预算

- `GenerationContext` 删除 `targetLength`/`budget`/`deeper(int)`/`withBudget`，
  新增 `name`（当前属性名，供语义推断）与 `deeper(String)`/`shouldExpand()`。
- 数组长度不再推算：`min..min(maxItems, min+3)`，硬上限 32。
- `JsonGenerator` 移除 `DEFAULT_TARGET_LENGTH` 与全部带长度参数的 `of(...)` 重载。
- 类型推断 `inferType` 从 `LengthEstimator` 内联进 `GenerationNode`（与 `pickType` 共用）。

## 影响

- 新增：`mock/jsonschema/impl/SemanticStringGenerator.java`。
- 修改：`GenerationContext`、`GenerationNode`、`JsonGenerator`、测试 `JsonGeneratorTest`。
- 删除：`LengthEstimator`（预算机制最后一个残留，复核无任何引用，删除待用户批准）。
- 改动文件：`GenerationContext`、`GenerationNode`、新增 `SemanticStringGenerator`、
  测试 `JsonGeneratorTest`（移除推荐长度/预算相关用例，新增语义化与回退用例）。
- 行为变化：输出更"像真实数据"；规模不再可通过参数调节（无长度入口）；
  结构深度由概率决定，同一 schema 多次生成的规模存在自然波动。
- 测试：mock 相关 86 项（JsonGenerator 36 / Automaton 21 / RegexStringGenerator 29）全部通过；
  flora-root 全量 2105 项中仅 `LogTest.testGlobalRetentionAcrossDays` 失败（既有日期边界问题，与本次无关）。
