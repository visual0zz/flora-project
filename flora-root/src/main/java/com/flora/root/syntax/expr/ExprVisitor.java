package com.flora.root.syntax.expr;

import java.util.List;

/**
 * 表达式 AST 遍历器。用户通过实现此接口自定义对表达式树的遍历/翻译/求值，
 * 经 {@link Expr#accept(ExprVisitor)} 分发到各节点类型。
 */
public interface ExprVisitor<R> {

    R number(String value);

    R ident(String name);

    R string(String value);

    R bool(boolean value);

    R unary(String op, R operand);

    R binary(String op, R left, R right);

    R ternary(R cond, R whenTrue, R whenFalse);

    R call(String name, List<R> args);
}
