# Decision: 基于熵评估重构 SecretCheck 的密钥判定

日期：2026-08-03
模块：flora-root（新增 `com.flora.entropy`）/ flora-osmetes（`SecretCheck`）

## 背景

原 `SecretCheck` 的"值形态"判定用一条正则近似高熵串：够长的字母数字混合即判为密钥。
在全仓库扫描下暴露两类问题：

1. **漏报**：键名正则要求密钥词完整独立成词，`client_secret`、`db_password` 这类
   带分隔符的复合键名匹配不到。
2. **误报爆炸**：整仓扫描一次报出 855 个 ERROR。典型来源是把代码表达式当成了值，
   例如 `allowedCharsets = List.of(StandardCharsets.UTF_8)`、
   `xmlns = "http://maven.apache.org/POM/4.0.0"`，以及贪心匹配把 XML 后续属性
   （`name="Gradle" foo="bar"`）吞进同一个值里。

## 决策

### 1. 在 flora-root 新增 `com.flora.entropy.Entropy`

熵评估从 `SecretCheck` 的正则里抽出来，做成零依赖的独立工具：

- `shannon(String)` / `shannon(byte[])`：按码点频率的香农熵，单位 bit/符号。
- `normalized(String)`：`shannon / log2(字母表大小)`，归一到 `[0,1]`，消除长度与
  字符集规模的影响，便于设阈值。
- `characterClasses(String)`：统计小写/大写/数字/符号四类，返回 `[0,4]`。
- `alnumClasses(String)`：**只**统计小写/大写/数字三类，返回 `[0,3]`。
- `complexityRatio(String)`：Deflate 最高压缩比，作为柯氏复杂度的工程近似。

采用的是**描述性熵**（对已知串本身的信息量度量），不是 KeePass 那种**生成性熵**
（`长度 × log2(字符池大小)`，度量的是生成该串的口令策略强度）。密钥检测面对的是
既成事实的字符串，没有"生成策略"可言，只能描述性度量。

**`alnumClasses` 与 `characterClasses` 并存的理由**：`characterClasses` 把 `-` `:` `/`
算作符号类，导致 `2024-01-15T103000Z` 凑够"大写+数字+符号"三类被误判为密钥。日期、
路径、ID 里分隔符极常见，故判密钥时改用忽略分隔符的 `alnumClasses`。

### 2. `SecretCheck` 只对"字面量右值"判定

这是压掉误报的关键。判定前先过 `isLiteralValue(raw, relativeFile)`：

- 纯数值（`NUMERIC`）一律排除 —— `SECRET_LEN = 32` 不是密钥。
- 加引号的串算字面量。
- 未加引号的串**仅在** `.properties` / `.yaml` / `.yml` 中算字面量，因为只有这些格式
  允许 `password: s3cr3t` 这种裸标量写法；源码 / JSON / XML 里密钥必然在引号内，
  未加引号的右值只可能是变量引用或表达式。

于是 `this.password = password`（字段赋值管道）、`TOKEN_COLORS = new HashMap<>()`
（构造表达式）自然被排除，不需要为它们写专门的豁免规则。

### 3. 值提取改为非贪心

`ASSIGNMENT` 的值捕获从 `[^\r\n]*` 改为「第一个引号字面量 或 首个裸 token（遇空白与
`; , ) } =` 即止）」，解决 `a = "x" b = "y"` 被并成一个值的问题。

### 4. 键名正则放宽

`KEY_NAME` 的边界从 `\b` 改为 `(?<![A-Za-z0-9])...(?![A-Za-z0-9])`，即**不把 `_` `-` `.`
当作单词字符**，从而 `client_secret`、`auth-token`、`db.password` 都能命中，而
`passwordless` 仍不命中。同时补充 `api_secret`、`client_secret`、`credential` 等词。

### 5. mock 豁免清单扩充

新增 URL（`https?://`，覆盖 xmlns）、代码符号（`( ) ; , { } [ ]`，覆盖表达式）、
算法与元数据词（PBKDF2 / HmacSHA / AES / StandardCharsets / Manifest / Version 等）。
算法词的边界写成 `(?:^|[^A-Za-z])` 前缀而**不加尾边界**，因为 `PBKDF2WithHmacSHA256`
是驼峰衔接，`\bPBKDF2\b` 匹配不到。

### 6. 阈值可配置

沿用 decision-02 的通用配置通道，支持 `secret.minLength`（默认 16）、
`secret.minClasses`（默认 3）、`secret.minEntropy`（默认 0.5）。非法值忽略并保留默认。

## 影响

- `flora-osmetes` 新增对 `flora-root` 的依赖（pom + `module-info` 的 `requires com.flora.root`）。
- 全仓库扫描的 secret ERROR 从 855 降到 0；剩余 WARNING 均为真实字面量上的键名命中
  （如测试里的 `password = "correct horse battery staple"`），属预期行为。
- 新增 `EntropyTest`（6 例）与 `SecretCheckTest` 的 5 个新用例（变量引用 / 数值常量 /
  构造表达式 / properties 裸标量 / YAML 裸标量前缀）。两模块全量测试通过。
- 曾考虑给检测用的假 Stripe key 加 `@SuppressWarnings("osmetes:secret")`，在实现
  「只判字面量」后该误报自行消失，故未保留抑制注解 —— 抑制注解会掩盖检查项本身的
  精度问题，能靠规则解决的就不用抑制。

## 已知遗留

`JsonGeneratorTest.recursiveDepthScalesWithBudget`（flora-root）用未固定种子的随机采样
断言 `deepAvg > shallowAvg`，样本量 30 时两者可能持平而偶发失败，与本次改动无关。
