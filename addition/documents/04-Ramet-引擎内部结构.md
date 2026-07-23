# 04 — Ramet 引擎内部结构

## 处理流水线

```
模板源码
  │
  ▼
Lexer（词法分析）
  │  src/main/java/com/flora/codegen/engine/parser/Lexer.java
  │  手工状态机，按字符遍历模板源码
  │  输出：Token 列表（PASSIVE / VAR / IF / FOR / MACRO / ...）
  │
  ▼
Parser（语法分析）
  │  src/main/java/com/flora/codegen/engine/parser/Parser.java
  │  递归下降、单 Token 前瞻
  │  输出：AST 节点列表
  │
  ▼
TemplateBody.render（渲染执行）
  │  src/main/java/com/flora/codegen/engine/runtime/TemplateBody.java
  │  遍历 AST，调用每个 Node 的 render()
  │  依赖 Context（变量作用域）和 FunctionRegistry（函数注册表）
  │
  ▼
输出字符串 → CodeGenUtil 写入文件
```

## 模块结构

```
com.flora.codegen
├── Ramet.java                    CLI 入口，文件系统编排
├── CodeGenUtil.java              代码生成门面（元数据解析 + 渲染 + 文件写入）
├── CompiledTemplate.java         预编译模板
├── TemplateFunction.java         SPI 接口
│
├── engine/
│   ├── CodeGenException.java     异常
│   ├── TemplateMeta.java         元数据处理（@Param / @Cartesian / @Path / @Config）
│   ├── TemplateUtils.java        工具方法
│   ├── Token.java                词素类型
│   │
│   ├── parser/
│   │   ├── Lexer.java            词法分析器
│   │   ├── Parser.java           语法分析器
│   │   ├── MetaParser.java       元数据块解析器
│   │   └── Lson.java             表达式解析器
│   │
│   ├── ast/
│   │   ├── Node.java             AST 节点基类
│   │   ├── TextNode.java         文本节点（被动区域输出）
│   │   ├── VarNode.java          ${} 插值节点
│   │   ├── IfNode.java           <#if> 条件节点
│   │   ├── ForNode.java          <#list> 循环节点
│   │   ├── MacroDefNode.java     <#macro> 宏定义节点
│   │   ├── MacroCallNode.java    <@name> 宏调用节点
│   │   ├── IncludeNode.java      <#include> 包含节点
│   │   ├── CommentNode.java      注释节点
│   │   └── MetaNode.java         元数据节点
│   │
│   └── runtime/
│       ├── Context.java          渲染上下文（变量作用域链 + 宏表）
│       ├── FunctionRegistry.java 函数注册表
│       ├── BuiltinFunc.java      内置函数实现
│       ├── RefResolver.java      AST 求值器
│       └── TemplateBody.java     渲染执行器
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

`TemplateMeta.expand()` 读取 `@Cartesian` 定义，计算各轴值的笛卡尔积，为每个组合生成一个 Variant（参数映射 + 输出路径）。如果所有 Variant 的输出路径相同，则合并为单文件输出。

## include 循环检测

`Context.includeChain` 维护当前 include 栈，每次 `<#include>` 前检查目标模板是否已在栈中，防止无限递归。
