# 04 — Ramet 引擎内部结构

## 核心抽象

引擎围绕「模板」这一概念建立了三个统一抽象，入口模板与 `<#include>` 子模板在
这些抽象层面完全同构，不再有「文本 vs 已编译产物」的区分：

- **`TemplateSource`**（`engine`）：模板原始来源，由 `key`（仓库定位标识）+ `text`（原始文本）组成。
- **`Template`**（`engine`）：解析产物，由 `key` + `nodes`（AST）+ `meta`（`<#meta>` 元数据）组成。
  统一持有「Lexer → WhitespaceTrimmer → Parser → 提取元数据」管线结果，提供 `parse(...)` 与 `render(...)`。
- **`TemplateRepository`**（`engine`）：按 key 提供 `Template` 的仓库接口，定义 `load(key)` 与
  `resolve(fromKey, path)`（路径解析：相对路径以发起 include 的文件目录为基准，`/` 开头以仓库根为基准）。
  内置 `none()`（无 include）与 `from(Map)`（内存）两个工厂；`Ramet` 提供基于文件系统的 `FileSystemTemplateRepository`。

## 处理流水线

```
模板源码（TemplateSource: key + text）
  │
  ▼
Lexer（词法分析）
  │  engine/lexer/Lexer.java
  │  手工状态机，按字符遍历模板源码
  │  输出：Token 列表（PASSIVE / VAR / IF / FOR / MACRO / ...）
  │
  ▼
WhitespaceTrimmer（空白规整）
  │  engine/lexer/WhitespaceTrimmer.java
  │  规整换行结构（NEW_LINE 及其后水平空白）
  │
  ▼
Parser（语法分析）
  │  engine/parser/Parser.java
  │  递归下降、单 Token 前瞻
  │  输出：AST 节点列表
  │
  ▼
Template.parse（封装上述三步 + 提取 <#meta> 元数据）
  │  engine/Template.java
  │  产出 Template（nodes + meta）
  │
  ▼
TemplateEngine.generate（编排）
  │  engine/TemplateEngine.java
  │  1. TemplateMeta.from(meta).expand() 展开 @Param/@Cartesian/@Path/@SkipWhen
  │  2. 对每个 Variant：TemplateBody.render(Context) 渲染
  │  3. OutputDecorator.decorate(...) 注入自动警告注释
  │
  ▼
输出字符串 → Ramet 写入文件
```

## 模块结构

```
com.flora.ramet                      公开包（仅导出此包）
├── Ramet.java                       CLI 入口，文件系统编排（扫描目录、读文件、写文件）
├── TemplateFunction.java            SPI 接口（函数扩展）
│
├── engine/                          引擎实现（不导出）
│   ├── TemplateEngine.java          代码生成编排器（解析 → 元数据展开 → 渲染 → 装饰）
│   ├── TemplateSource.java          模板原始来源（key + text）
│   ├── Template.java                解析产物（key + nodes + meta），统一解析管线
│   ├── TemplateRepository.java      模板仓库接口 + none()/from() 工厂
│   ├── OutputDecorator.java         自动警告注释注入（按扩展名选注释风格）
│   ├── CodeGenException.java        异常
│   ├── TemplateUtils.java           工具方法（真值、集合展开、反射属性访问、异常构建）
│   ├── ConfigKey.java               @Config 配置项常量
│   ├── LazyArg.java                 惰性参数（支持函数短路求值）
│   │
│   ├── model/
│   │   ├── TemplateMeta.java        元数据处理（@Param / @Cartesian / @Path / @Config / @SkipWhen）
│   │   ├── Token.java               词素类型
│   │   ├── Lson.java                表达式解析器
│   │   └── LsonNumber.java          数字字面量
│   │
│   ├── lexer/
│   │   ├── Lexer.java               词法分析器
│   │   └── WhitespaceTrimmer.java   空白规整
│   │
│   ├── parser/
│   │   ├── Parser.java             语法分析器
│   │   └── MetaParser.java         元数据块解析器
│   │
│   ├── ast/
│   │   ├── Node.java                AST 节点基类
│   │   ├── TextNode.java            文本节点（被动区域输出）
│   │   ├── VarNode.java             ${} 插值节点
│   │   ├── IfNode.java              <#if> 条件节点
│   │   ├── ForNode.java             <#for> 循环节点
│   │   ├── MacroDefNode.java        <#macro> 宏定义节点
│   │   ├── MacroCallNode.java        <@name> 宏调用节点
│   │   ├── IncludeNode.java         <#include> 包含节点
│   │   ├── CommentNode.java         注释节点
│   │   ├── MetaNode.java            元数据节点
│   │   ├── BreakNode.java / ContinueNode.java   循环控制节点
│   │
│   └── runtime/
│       ├── Context.java             渲染上下文（变量作用域链 + 宏表 + TemplateRepository）
│       ├── FunctionRegistry.java    函数注册表
│       ├── BuiltinFunc.java         内置函数实现
│       ├── RefResolver.java         AST 求值器（Lson 表达式求值 + 引用解析 + 成环检测）
│       ├── TemplateBody.java        渲染执行器（遍历 AST，调用每个 Node 的 render()）
│       ├── BreakSignal.java / ContinueSignal.java   循环控制异常
```

## 表达式求值路径

```
Lson.parse("a.b[c]") → AST：PropertyAccess(Reference("a"), "b", IndexAccess("c"))

RefResolver.evalCtx(ast, context) →
  1. Reference("a") → Context.lookup("a") → 变量值
  2. PropertyAccess → TemplateUtils.getProperty(值, "b")
  3. IndexAccess → 列表索引
  ...
  FunctionCall("greaterThan", [left, right]) → FunctionRegistry.apply("greaterThan", [左值, 右值])
```

## 笛卡尔积展开

`TemplateMeta.expand()` 读取 `@Cartesian` 定义，计算各轴值的笛卡尔积，为每个组合生成一个 Variant
（参数映射 + 输出路径）。如果所有 Variant 的输出路径相同，则合并为单文件输出（Combine 轴值以列表形式展开）。

## include 机制与循环检测

- `IncludeNode.render` 对路径表达式求值后，交给 `Context.repo`（即 `TemplateRepository`）：
  先 `resolve(source, path)` 解析为 key，再 `load(key)` 加载对应 `Template`，创建子上下文
  （`source` 更新为目标 key）递归渲染。路径解析的两种基准（相对目录 / 仓库根）由仓库实现决定。
- `Context.includeChain` 维护当前 include 栈，`<#include>` 前检查目标 key 是否已在栈中，防止无限递归。
- `Ramet` 通过嵌套的 `FileSystemTemplateRepository` 提供子模板：按需读取并解析、带缓存，
  不再在启动期急切预编译目录下所有模板。
