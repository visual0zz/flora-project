/**
 * 语法分析包：共享词法基元 + 多个独立分析器模块。
 * <p>共享基元（{@code com.flora.syntax} 顶层）：{@code Token}、{@code TokenType}
 * （宽泛词法类别，不含具体运算符）、{@code SyntaxException}（带位置错误）。
 * 语法层节点（如 bracket 的 {@code BracketNode}、expr 的 {@code Expr}）在各模块
 * 内独立定义，不跨模块共享。</p>
 * <p>子模块：{@code com.flora.syntax.bracket} 括号结构分析器、
 * {@code com.flora.syntax.expr} 语义可注入的表达式分析器。</p>
 */
package com.flora.syntax;
