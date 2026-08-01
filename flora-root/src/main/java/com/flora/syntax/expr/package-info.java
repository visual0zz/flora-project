/**
 * 表达式分析器：C/Java 风格运算符表达式解析。
 * <p>入口为 {@link ExprParser}；对外以 {@link Semantics} 的 lambda 注入语义为主
 * （用户定义 DSL 中每个运算符的实际运算），AST {@link Expr} 公开供高级自定义遍历。
 * 运算符集合固定为 C/Java 风格，见 {@code OpPrecedence}。</p>
 */
package com.flora.syntax.expr;
