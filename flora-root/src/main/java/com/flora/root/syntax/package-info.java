/**
 * 语法分析包：共享词法基元 + 多个独立分析器模块。
 * <p>共享词法基元（{@code Tokenizer}、{@code Token}/{@code TokenKind}、异常体系
 * {@code SyntaxException}/{@code ParseException}）统一收在 {@code com.flora.syntax.common}，
 * 由 {@code bracket}/{@code expr}/{@code peg} 各分析器模块共同消费。</p>
 * <p>子模块：{@code com.flora.syntax.bracket} 括号结构分析器、
 * {@code com.flora.syntax.expr} 语义可注入的表达式分析器、
 * {@code com.flora.syntax.peg} 通用 PEG 文法引擎。</p>
 */
package com.flora.root.syntax;
