# 通用语法解析器设计（com.flora.syntax）— 混合方案（C，g4 对齐）

日期：2026-08-05
主题：在 `com.flora.syntax` 包设计通用语法解析器，使用方式类比 `java.util.regex`：
输入一段"语法定义字符串"，编译为内存中的高效识别器，识别其它字符串并得到语法树。
"编译"指编译成内存形态（类比 `Pattern` 的 `Node` 树），不生成源码/磁盘文件。

设计要点（经多轮确认）：
- **输入语法 = g4（ANTLR）记法**：接收的字符串即为一份合法 g4 文法内容。
- **后端处理 = 自有 PEG 内存解释器**：有序选择、packrat、递归规则、token 级匹配。
- **词法/文法两阶段**：用户写一份文法串，引擎内部编译出词法器 + token 级 PEG。
- **最基础层、零依赖**：不依赖 `com.flora.syntax` 中既有 `SyntaxException`/`Tokenizer`/`Token`/`TokenType`；自带异常、自带词法器。
- 早期 scannerless（字符级 PEG）方案已废弃。

## 1. 形式化：PEG，但分两层

- **文法层**：PEG（解析表达式文法），有序选择 `|`、递归规则、谓词 `&`/`!`、
  `* + ?` 重复——与正则类比一致，无歧义，编译为递归下降 matcher + packrat 记忆化。
- **词法层**：词法规则用字符级表达式（字面量/字符类/`.`/重复/选择/谓词），
  编译为分词器（MVP 用最长匹配 matcher 列表，可后续升级为 DFA）。
- **对外一份 g4 文法串，对内两阶段**：用户不感知分词细节；引擎把大写规则编译成分词器、
  把小写规则编译成在 token 上跑的 PEG。兼顾"单串声明"的通用手感与两阶段的性能/整洁。

## 2. 语法定义字符串（对齐 g4）

语法串即一份 g4（ANTLR）文法内容；下表给出与本引擎对齐的记法要点（完整文法见 ANTLR 文档）：

| 要素 | g4 记法 | 本引擎处理 |
|------|---------|-----------|
| 词法规则 | `Name : ... ;`（大写开头） | 编译为分词器 token 规格 |
| 文法规则 | `name : ... ;`（小写开头） | 编译为 token 流上的 PEG |
| 选择 | `a \| b` | **有序选择**（首个命中即定，非 ANTLR 前瞻预测，见 2.4） |
| 序列 | 空白 | 同 PEG |
| 重复 | `*` `+` `?` | 同 PEG |
| 分组 | `( )` | 同 PEG |
| 字符串字面量 | `'...'`（单引号） | 文法层隐式 token |
| 字符类 | `[...]` | 同 |
| 任意字符 | `.`（仅词法层） | 同 |
| 备选标签 | `alt # Label` | 该候选子树命名为 `Label` |
| 词法片段 | `fragment Name : ... ;` | 辅助词法规则，不产 token |
| 跳过 | `WS : [ \t\n]+ -> skip ;` | 匹配后丢弃，不进 token 流 |
| 入口 | `@start name;` 或首个文法规则 | 入口规则 |
| 句法前瞻 | `&e` / `!e`（**PEG 扩展，g4 无对应**） | 前瞻 / 负前瞻，不消费 |

> 记法对齐 g4，但**引擎语义是 PEG 内存解释器**（非 ANTLR 的 ALL(*) 编译器）。能力边界见 2.4。

转义：`\' \\ \n \t \r \f \b \/ \uXXXX \xHH`，字符类内另有 `\] \[ \-`。**未知转义编译期报错**，不静默当作字面字符。注释：`//` 到行尾 或 `/* ... */` 块注释（`#` 专用于备选标签 `alt # Label`，不作注释符）。

### 2.1 Token 宇宙与分词策略

- **Token 宇宙** = 所有大写词法规则 ∪ 文法规则中出现的全部字符串字面量（如 `'{'` `'true'`）。
  后者即"隐式 token"（ANTLR 同理）。
- 分词器在每个位置尝试全部 token 规格，**最长匹配胜出**；平局按声明顺序。
  `-> kind(SKIP)` 的词法规则（如 `WS`）匹配后标为 `SKIP`、**仍留在 token 列表中**，但 parser 自动跳过（不成为文法节点）——因此字符串内部的空格
  天然安全（`String : '"' ~["]* '"' ;` 把整串当一个 token 吃掉），文法规则无需为空白写额外规则。

### 2.2 树（捕获）语义

- **文法规则应用** `r : body` → 名为 `r` 的节点，子节点为 `body` 产生的所有捕获。
- **词法规则引用** `Upper` → 叶子节点，名为 `Upper`，`text()` 为匹配到的 token 文本。
- **备选标签** `#Label`（置于某候选后）→ 该候选子树命名为 `Label`（用于区分 `|` 的各分支）。
- **字符串字面量 / 字符类（文法层）** → 叶子节点，名取其源码文本（`'{'` → 名为 `'{'`）。
  标点/运算符默认进树；需要干净树时由使用方在 visitor 中剪枝（与 g4 行为一致，不引入非标准静音符）。
- 词法层 `-> kind(SKIP)` 的内容仍在 token 列表中，但 parser 自动跳过，故不进树。

### 2.3 选项（GrammarOptions）

- `caseInsensitive(boolean)`：字面量与字符类大小写不敏感。
- `lexerLongestMatch(boolean)`：分词最长匹配（默认开，lex 语义）；关则为 PEG 有序（首匹配）。

### 2.4 支持 / 不支持的 g4 语法（明确清单）

本引擎 = **g4 记法兼容的 PEG 解释器**。下表逐条列出每个 g4 语法特性的支持状态。
除"刻意不做"与"语义差异"两类外，其余 g4 记法均对齐；"需引擎扩展"类为 g4 合法语法，
当前 PEG 暂未实现，已列入实现计划（不换引擎类别即可补齐）。

| g4 语法 | 状态 | 说明 |
|---------|------|------|
| 词法规则 `Name : ... ;`（大写开头） | ✅ 支持 | 编译为分词器 token 规格 |
| 文法规则 `name : ... ;`（小写开头） | ✅ 支持 | 编译为 token 流上的 PEG |
| 选择 `a \| b` | ✅ 支持（有序） | 首个命中即定，非 ANTLR 前瞻预测（见语义差异） |
| 序列（空白连接） | ✅ 支持 | |
| 重复 `* + ?` | ✅ 支持 | |
| 分组 `( )` | ✅ 支持 | |
| 字符串字面量 `'...'`（单引号） | ✅ 支持 | 文法层隐式 token |
| 字符类 `[...]` | ✅ 支持 | |
| 任意字符 `.`（仅词法层） | ✅ 支持 | |
| 备选标签 `alt # Label` | ✅ 支持 | 该候选子树命名 |
| 词法片段 `fragment Name : ... ;` | ✅ 支持 | 辅助词法规则，不产 token |
| 跳过 `-> skip`（g4） | ❌ 不兼容 | 本引擎以 `-> kind(SKIP)` 取代；词法注解统一为 `kind` 一种，不再接受 g4 的 `-> skip` |
| 类别标注 `-> kind(KIND)` | ✅ 支持 | 将词法规则归入引擎内置的通用 `TokenKind` 体系（从内置词汇表选取，非文法自定义类型）。**引擎不校验模式与该类别语义是否相符**，归类由作者自担（类比 ANTLR `-> channel(HIDDEN)` 亦不校验 token 真是空白）。词汇表含特殊值 `SKIP`（词法期丢弃，永不在存活 token 上出现），故"丢弃"也经 `kind` 表达 |
| 入口 `@start name;` | ✅ 支持 | 或首个文法规则 |
| 句法前瞻 `&e` / `!e` | ✅ 支持 | PEG 扩展（g4 无对应），前瞻/负前瞻不消费 |
| 左递归 `expr : expr '+' term` | ✅ 已实现 | Warth 种子生长，直接左递归可解析且天然左结合；间接左递归编译期拒绝（见 §7 阶段 10） |
| 词法模式 `mode` / `pushMode` / `popMode` | ✅ 已实现 | 词法器模式栈；`->` 支持逗号多命令；隐式字面量 token 任意模式可匹配（见 §7 阶段 11） |
| 嵌入动作 `{...}` | ❌ 刻意不做 | 需执行用户代码，违零依赖/纯声明/基础层目标；用 `ParseTree` + visitor 替代 |
| 语义谓词 `{...}?` | ❌ 刻意不做 | 同上；常见情形可用 `&` / `!` 句法前瞻近似 |
| 多通道 `channels` 及 `-> channel` | ❌ 刻意不做 | 隐藏通道由 `kind ∈ Trivia` 覆盖、`-> skip` 由 `kind(SKIP)` 覆盖；任意自定义通道无需求 |
| `-> type` | ❌ 刻意不做 | token 类型已由 `kind` + `typeName` 表达，无需改类型命令 |
| `import` | ❌ 刻意不做 | 本引擎为单串运行时解释器，文法组合由调用方拼接字符串完成，无需导入机制 |
| `returns` / 规则参数 / `locals` | ❌ 刻意不做 | 语义值由消费方 visitor 投影（同嵌入动作处理），纯结构解析无需规则属性槽 |
| `|` 的语义（有序 vs ALL(*) 预测） | ⚠️ 语义差异 | 本引擎取有序选择；有歧义文法可能与 ANTLR 产出不同树，须文档/报错写清 |
| 结合性注解 `assoc` | ⚠️ 语义差异 | 本引擎靠重复顺序天然左结合，注解忽略（通常无害） |

**关键结论**
- 除"刻意不做"的多类（动作 / 语义谓词 / 多通道 / `-> type` / `import` / `returns` 等）与"语义差异"的 `|`，**其余 g4 记法全部对齐，无兼容问题**。
- 左递归（Warth）与词法模式两类**已实现**（见 §7 阶段 10 / 11），引擎扩展面已清空：其余 g4 特性要么已对齐、要么被 `kind` / visitor 模型刻意覆盖。
- 多通道 / `import` / `returns` 等因已被 `kind` / visitor 模型覆盖或无需求而**刻意不做**；`|` 为**刻意的有序选择**，非能力缺失。
- 词法注解统一为 `-> kind` 一种：g4 的 `-> skip` 以 `-> kind(SKIP)` 取代（见上表），属刻意收敛，非能力缺失。

## 3. 编译后的内存形态（高效形态）

`Grammar.compile` 步骤：

1. **元解析**：手写递归下降解析 g4 文法串 → `RuleDef[]` AST（区分 lexer/parser 标记、记录 `#Label`）。
2. **校验**：词法规则体不含文法规则引用；所有引用可解析；检测直接/间接左递归（PEG 左递归
   死循环，编译期报错）；拒绝 2.4 列出的不支持特性；**词法规则禁止可匹配空串（nullable）**——
   否则会产出零宽 token 致词法器不前进而陷入死循环，编译期即报错。
3. **编译词法层**：收集 token 宇宙，把每个词法规则编译为字符级 `CharMatcher`；组装成分词器
   `Lexer`（持有 token 规格列表 + skip 集合 + 最长匹配策略）。
4. **编译文法层**：把每个文法规则编译为 token 级 `Matcher` 节点树：
   - `TokenMatch(UPPER_ID)`：下一个 token 属于该词法规则
   - `LiteralToken(String)`：下一个 token 文本等于该字面量
   - `RuleRef(int)` / `Sequence` / `Choice` / `Repeat` / `And` / `Not` / `Capture` / `Rule`
5. `Grammar` 持有 `Lexer lexer`、`Rule[] parserRules`、入口索引、选项。

这套 `Lexer` + `Matcher` 树即"高效内存形态"。可选优化：词法层升级为 DFA；文法层 matcher 树
压平为指令数组 + 解释器（提缓存局部性）。

## 4. 公共 API（与 scannerless 版一致）

置于 `com.flora.syntax.peg`（公开 API，已导出）；内部 `com.flora.syntax.peg.impl` 不导出。
（因 `com.flora.syntax` 已存在旧 `Token` / `Tokenizer` / `TokenType` 等类型，新引擎以独立子包与其
并存、互不耦合，类名仍按本文档：`Grammar` / `Token` / `ParseTree` / `TokenKind` 等。）
引擎完全自包含：自带 `ParseTree`、`Lexer` 及自有异常 `ParseException` / `GrammarException`，
**不依赖** `com.flora.syntax` 中任何既有类型（`SyntaxException` / `Tokenizer` / `Token` / `TokenType`）。
它是本包最基础的层，目标作为既有解析器（expr/bracket）底层结构的可替换基座。

```java
public final class Grammar {
    public static Grammar compile(String definition);        // 非法 g4 子集 → 抛 GrammarException
    public static Grammar compile(String definition, GrammarOptions options);
    public ParseOutput parse(CharSequence input);        // 全量匹配，失败抛 ParseException；返回 token 列表 + 树
    public ParseOutput tryParse(CharSequence input);   // 不抛，含 success / tokens / tree / error
    public Recognizer recognizer(CharSequence input);  // 有状态，类比 Matcher
    public String entry();
}

public final class Recognizer {
    public boolean matches();        // 从当前位置匹配到末尾
    public boolean lookingAt();
    public ParseTree tree();
    public int end();
    public ParseException failure();
    public Recognizer reset(CharSequence input);
    public Recognizer region(int start, int end);
}

/**
 * 一次 parse 的结果：同时持有词法层输出（token 列表）与解析层输出（语法树），供链式分别取出。
 * parse() 失败即抛 ParseException（取对象前已抛）；tryParse() 不抛，借 success()/error() 判断。
 */
public final class ParseOutput {
    public boolean success();              // 是否全量匹配成功
    public List<Token> tokens();           // 词法层输出：全部 token 列表（含 kind=SKIP 的；parser 对 Trivia 与 SKIP 自动跳过，消费方按需按 kind 过滤）
    public ParseTree tree();               // 解析层输出：语法树（success 时非空）
    public ParseException error();         // 失败时的错误（带行列 / 期望项；成功时为 null）
}

/** 词法层输出（公开 API）：一个 token。kind 为引擎内置通用类别，typeName 为匹配到的文法规则名。 */
public final class Token {
    public TokenKind kind();        // 引擎内置类别（Whitespace / Identifier / NumberLiteral / Custom ...）
    public String typeName();       // 文法规则名（如 "Number"、"String"）或隐式 token 名
    public String text();
    public int start(); public int end();
    public int line(); public int column();
}

/** 语法树节点：引擎内置固定的通用类型（密封结构树），不按文法生成子类。 */
public sealed interface ParseTree permits ParseTree.RuleNode, ParseTree.TokenNode {
    String name();            // 导航标识：#Label 优先，否则文法规则名 / 词法规则名 / 字面量源码
    String text();            // 匹配子串
    int start();              // 字符偏移（非 token 下标）
    int end();
    List<ParseTree> children();
    boolean isLeaf();
    default Token token() { return null; }   // 仅 TokenNode 持有底层的 Token

    /** 非终结节点：一条文法规则（或带 #Label 的候选）的应用，持有子节点。 */
    record RuleNode(String ruleName, String label,
                    List<ParseTree> children, int start, int end) implements ParseTree {
        public String name() { return label != null ? label : ruleName; }
        public boolean isLeaf() { return false; }
    }

    /** 终结叶子：包裹一个 Token（词法规则引用 / 字面量叶子）。 */
    record TokenNode(Token token) implements ParseTree {
        public String name() { return token.typeName(); }
        public String text() { return token.text(); }
        public int start() { return token.start(); }
        public int end() { return token.end(); }
        public List<ParseTree> children() { return List.of(); }
        public boolean isLeaf() { return true; }
        public Token token() { return token; }
    }
}

/** 文法编译期错误（未定义规则引用、词法规则体非法、左递归、不支持的 g4 特性等），由 compile 抛出。 */
public final class GrammarException extends RuntimeException {
    public GrammarException(String message);
}

/** 识别期错误（输入不匹配），由 parse 抛出；携带失败位置与期望项。 */
public final class ParseException extends RuntimeException {
    public int line();
    public int column();
    public int offset();
    public String expected();   // 期望匹配的描述
}
```

### 4.1 通用元素类型（不生成每文法代码）

专用工具（如 ANTLR）会为每份文法**生成**专属类型（`ExprContext extends ParserRuleContext`、具名字段
`expr()` / `op()`、`XVisitor<T>`）。我们是**通用、内存、零依赖**工具，文法是运行期的数据（字符串），
不为每份文法生成 Java 类型。因此只用两类统一的通用类型（均引擎内置、固定，不按文法生成子类）表示所有语法元素：

- **`TokenKind`（引擎内置的通用词法类型继承树）**：由引擎一次性定义、固定的少量抽象，
  **不**由文法引入新类型。采用密封接口 + 记录实现的两层体系，例如：

  ```
  sealed interface TokenKind {
      sealed interface Trivia extends TokenKind permits Whitespace, LineBreak, Comment {}
      sealed interface Word   extends TokenKind permits Identifier, Keyword {}
      sealed interface Literal extends TokenKind permits NumberLiteral, StringLiteral, BooleanLiteral {}
      sealed interface Symbol extends TokenKind permits Operator, Punctuation {}
      record Whitespace()    implements Trivia {}
      record LineBreak()     implements Trivia {}   // 换行及其前后空白
      record Comment()       implements Trivia {}
      record Identifier()    implements Word {}
      record Keyword()       implements Word {}
      record NumberLiteral()  implements Literal {}
      record StringLiteral()  implements Literal {}
      record BooleanLiteral() implements Literal {}
      record Operator()      implements Symbol {}
      record Punctuation()   implements Symbol {}
      record Eof()           implements TokenKind {}
      record Skip()          implements TokenKind {}   // parser 自动跳过（取代 g4 的 -> skip）；仍保留在 token 列表中（tokens() 返回全部 token）
      record Terminal()      implements TokenKind {}   // 文法内联字符串字面量终端（如 '{'、'true'），非具名词法规则
      record Custom()        implements TokenKind {}   // 未标注 -> kind 的具名词法规则兜底（文法自定义类别）
  }

  未标注 `-> kind` 的**具名词法规则**（如 `WS`、`Number`），其 token 的 `kind` 默认为 `TokenKind.CUSTOM`——
  保证每个 token 都非 null 有类，纯 g4 文件（从不写 `-> kind`）也能无缝运行；`CUSTOM` 之外的归类需文法显式选取。
  文法规则中的**字符串字面量终端**（如 `'{'`、`'true'`、`'null'`）非具名词法规则、无法标 `-> kind`，
  其 `kind` 固定为 `TokenKind.TERMINAL`，与 `CUSTOM` 区分（`CUSTOM` 是作者未归类的具名词法规则，
  `TERMINAL` 是文法内联书写的字面量终端）。

  **可选词汇表（语法文件 `-> kind(X)` 的 X）**：`WHITESPACE` / `LINE_BREAK` / `COMMENT` /
  `IDENTIFIER` / `KEYWORD` / `NUMBER_LITERAL` / `STRING_LITERAL` / `BOOLEAN_LITERAL` / `OPERATOR` /
  `PUNCTUATION` / `TERMINAL` / `SKIP` / `CUSTOM`。其中 `Trivia` 是**组别（密封接口）而非可选值**——语法文件
  只写其叶子常量（`WHITESPACE` / `LINE_BREAK` / `COMMENT`）；引擎以 `kind instanceof TokenKind.Trivia` 判定
  "保留 + parser 自动跳过"（隐藏通道语义）。`SKIP` 与 `Trivia` 同由 parser 自动跳过，但**均保留在 token 列表中**（`tokens()` 返回全部 token，消费方按需按 `kind` 过滤）。`EOF` 为引擎输入的结束哨兵，作者不可选。
  ```
  消费方可按 `instanceof TokenKind.LineBreak` 等做类型匹配，得到"少量抽象"而非扁平一团。

- **`Token`（公开 API，词法层输出）**：`(kind: TokenKind, typeName: String, text, start, end, line, column)`。
  - `kind` = 引擎内置通用类别（上树），用于跨文法的通用判断（如"是否空白/换行/注释"）。
  - `typeName` = 匹配到的**文法规则名**（如 `"Number"`、`"String"`），用于文法内特指。
  两者并存：通用归类走 `kind`，文法特指走 `typeName`；引擎未覆盖的类别可仅靠 `typeName` 区分。

- **文法如何归入 `kind`（不定义新类型）**：词法规则通过内置 `-> kind(KIND)` 命令从引擎词汇表
  **选取**一个已有类别（非自定义）；未标注一律 `TokenKind.CUSTOM`，**不做任何命名约定猜测**。
  例如用户示例——把换行及其前后空白合为一个 `LineBreakToken`：

  ```
  Line : [ \t]* '\n' [ \t]* -> kind(LINE_BREAK) ;   // 换行+前后空白 → 一个 LineBreakToken
  WS   : [ \t]+          -> kind(WHITESPACE) ;       // 纯水平空白 → WhitespaceToken
  ```

  这里"合在一起"是靠词法规则的**模式**（消费哪些字符）实现；"LineBreakToken 类型"是引擎内置的
  `TokenKind.LINE_BREAK`。文法只定义模式、只选取内置类别，**从不定义新类型**——与"不能靠语法
  定义 Token 类型"一致。
- **`ParseTree`（解析层输出，公开 API）**：`sealed interface ParseTree`，引擎内置的**结构角色**
  密封树，不按文法生成子类。两个分支对应"节点是什么结构位置"的通用分类（类比 `TokenKind` 对应
  "token 是什么词法类别"）：
  - `RuleNode(ruleName, label, children, start, end)`：非终结节点，一条文法规则（或带 `#Label`
    的候选）的应用，持有子节点。`name()` 取 `label`（若有）否则 `ruleName`。
  - `TokenNode(Token token)`：终结叶子，包裹一个 `Token`（`token()` 取回）。`name()` 取
    `token.typeName()`。它是词法层 `Token` 与解析层 `ParseTree` 之间**有意的适配缝**（Adapter，
    不新增行为，仅让叶子能统一进入 `children()` 与 `RuleNode` 并列），非冗余类型；不采用令 `Token`
    直接实现 `ParseTree` 的合并方案，以保持词法层与解析层分离（与 g4/ANTLR 的 `TerminalNode` 惯例一致）。
  通用维度 = `instanceof RuleNode` / `TokenNode`（跨文法通用，可写通用 walker / visitor /
  pretty-printer）；具体维度 = `name()`（规则名 / `#Label`）/ `token.typeName()`（文法特指）。
  与 `Token` 的 `TokenKind`（通用）+ `typeName`（特指）完全对称。不需要 `ErrorNode`：PEG 全或无，
  失败即抛 `ParseException`，不像 ANTLR 建错误节点做错误恢复。

节点的**身份靠 `name()` 字符串**区分，而非靠 Java 类型。消费方用名字匹配即可，无需为每份文法
编译出一套类——这正是"通用"与"不出文件"的代价与收益所在。

### 4.2 消费方如何导航（无生成类型时）

没有 `expr()` 这类具名方法，消费方用以下等价手段：

- **按名访问**：`node.child("expr")` 取首个名为 `expr` 的子节点；`node.children("expr)` 取全部；
  `node.child(int)` 按下标。
- **visitor / walker**：提供泛型 `ParseTreeVisitor<T>`（或函数式 `Map<String, Function<ParseTree, T>>`），
  内部按 `node.name()` 分派——与 ANTLR 的 `XVisitor` 能力等价，只是键是字符串而非生成的方法名。
- **路径/查询**（可选）：`node.select("//expr/term")` 之类 XPath 风格选择（ANTLR 有 `Tree.xpath`）。
- **自行投影**：消费方在 visitor 里把领域类型（如 `Expr`）作为返回值 `T` 构造出来——
  与 ANTLR 用户写 visitor 返回自有类型完全一样，只是我们的 visitor 键是 `name` 字符串。

> 结论：用"两个通用类型 + name 驱动导航 + 可选 visitor"替代专用工具的"每文法生成类型"。
> 能力不弱（visitor 同样能产出任意领域模型），且守住零依赖、内存、不出文件的定位。

## 5. 识别器运行时

- `Recognizer` 先把 `CharSequence` 交给 `Lexer` 切成 `List<Token>`（遇到无规则匹配的字符即报
  词法错误，带位置）。
- 再在 token 流上跑 PEG（`match(ruleIndex)`，packrat 记忆键 `(ruleIndex, tokenPos)`），构建
  `ParseTree`。节点 `start()`/`end()` 由底层 token 的字符偏移回填，故树仍带字符级位置。
- parser 每次匹配前自动跳过 `kind ∈ Trivia` 或 `kind == SKIP` 的 token（隐藏通道 / 跳过语义），
  但它们**仍保留在 `tokens()` 返回的全部 token 列表中**（消费方按需按 `kind` 过滤）。
- 失败收集最远失败位置 + 期望项，由自有 `ParseException` 附带行列抛出。

## 6. 与现有 syntax 包的关系

- 引擎**零依赖** `com.flora.syntax` 中既有类型：不依赖 `SyntaxException`（自带 `ParseException` /
  `GrammarException`）、不依赖 `Tokenizer`（自带语法驱动的词法器 `Lexer`）、不借用既有 `Token` /
  `TokenType`（自带的 `Token` / `TokenKind` / `ParseTree` 为公开 API，实现细节置于 `com.flora.syntax.impl`）。
- 它是本包**最基础的层**：既有 `syntax.expr` / `syntax.bracket` 的底层结构（含旧 `Tokenizer`）
  将来可重建于本引擎之上、或被其替换；过渡期二者并存、互不耦合。
- 本引擎因此保持自包含、零对外依赖，契合 `flora-root` 零依赖定位，可作为项目语法能力的统一基座。

导出：`com.flora.syntax` 导公共 API；`com.flora.syntax.impl` 不导出。

## 7. 实现阶段

MVP（核心可用）：
1. g4 子集元解析器 + `RuleDef` AST（标 lexer/parser、记录 `#Label`）。
2. 校验器（引用解析、词法层禁用文法引用、2.4 不支持特性拒绝；**左递归暂检测并编译期报错**，待阶段 10 支持）。
3. 词法层编译：`Lexer`（token 宇宙 + 最长匹配 + `kind` 标注：`kind(SKIP)` 标记跳过、`kind ∈ Trivia` parser 自动跳过）。
4. 文法层编译：token 级 `Matcher` 树（Sequence / Choice / Repeat / RuleRef / LiteralToken / TokenMatch / And / Not / Capture）。
5. `Recognizer` 运行时：先 lex 后 parse + packrat + 建树 + 位置回填；`tokens()` 返回全部 token。
6. 标签 `#Label` + 选项（`GrammarOptions`）+ `&`/`!` 扩展。
7. `ParseException` 增强（位置→行列、期望项）。
8. `ParseOutput` 双输出（tokens + tree）链式 API。
9. 测试：g4 记法定义计算器、JSON 子集、嵌套括号，验证 token 流与语法树。

引擎扩展（本次计划，对应 §2.4 🔧）：
10. **左递归支持（Warth 种子生长）**：将直接左递归改写为种子生长循环，使 `expr : expr '+' term` 等自然左结合文法可解析；阶段 2 的"检测报错"随之转为"支持"。
11. **词法模式（mode / pushMode / popMode）**：词法器加模式栈，按上下文切换启用的词法规则集，处理字符串 / 注释内部等上下文敏感词法。

**实现状态（2026-08-05）**：阶段 1–11 已全部实现并测试通过（`flora-root` 模块，`com.flora.syntax.peg`，
`GrammarTest` 13 项 + `GrammarFeatureTest` 19 项，共 32 项：JSON / 计算器 / 嵌套括号 / `#Label` /
错误定位 / 链式 `ParseOutput` / 显式 `kind` 与 `SKIP` / 空串拒绝 / 直接左递归种子生长 / 间接左递归拒绝 /
词法模式 / 字符类转义取反 / 最长匹配平局 / 有序模式 / caseInsensitive / 谓词 / Recognizer / Visitor /
token 位置 / 片段 / 优先级结合性）。`->` 支持 `kind` / `mode` / `pushMode` / `popMode` 逗号多命令；
`kind` 词汇表 12 项 + `TERMINAL`（隐式字面量）。**不做命名约定猜测**：kind 只能显式 `-> kind(...)`
指定，未标注一律 `CUSTOM`；跳过仅经 `-> kind(SKIP)`，无 skipRule 选项。

## 8. 完整示例（JSON 子集，g4 对齐记法）

```
@start value;

value  : object | array | String | Number | 'true' | 'false' | 'null' ;
object : '{' (String ':' value (',' String ':' value)*)? '}' ;
array  : '[' (value (',' value)*)? ']' ;

fragment DIGIT : [0-9] ;
String : '"' ~["]* '"' ;
Number : DIGIT+ ('.' DIGIT+)? ;
WS     : [ \t\n\r]+ -> kind(SKIP) ;
```

识别：

```java
Grammar g = Grammar.compile(json);          // json 为上面的 g4 文法串
ParseOutput out = g.parse("{\"a\":[1,2.5],\"b\":null}");
List<Token> toks = out.tokens();            // 词法层输出：存活 token 列表
ParseTree t      = out.tree();              // 解析层输出：语法树

// 链式（一次 parse 同时取两份输出）：
g.parse(input).tokens();   // → List<Token>
g.parse(input).tree();     // → ParseTree

// t.name() == "value"
// 子节点：object -> { String("a"), value(Number 1), String("b"), value(null) }
// 其中 String 是词法规则引用 → 叶子名为 "String"，text() 含引号内文本
```

内部流程：`Lexer` 把输入切成 `[String("a"), {, [, Number(1), ,, Number(2.5), ], ,, String("b"), :, null, ]`
（`WS` 标为 SKIP，parser 自动跳过，仍在 token 列表）→ PEG 在 token 上识别 `value` → 建树。
