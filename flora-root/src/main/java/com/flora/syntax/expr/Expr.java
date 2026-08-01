package com.flora.syntax.expr;

import java.util.List;

/**
 * 表达式 AST。仅描述结构，不承载求值逻辑——求值由 {@link Semantics} 注入。
 * <p>运算符以符号字符串表示（如 {@code "+"}、{@code "<<"}、{@code "&&"}）。
 * 每个节点携带在源表达式中的起始位置 {@link #pos()}（索引，供错误定位）。</p>
 */
public sealed interface Expr {

    /** 节点在源表达式中的起始位置（字符索引）。 */
    int pos();

    /** 数字字面量（原样保留，语义层自行解析）。 */
    record Number(String value, int pos) implements Expr {
    }

    /** 标识符。 */
    record Ident(String name, int pos) implements Expr {
    }

    /** 字符串字面量（值已解码，不含外层引号）。 */
    record Str(String value, int pos) implements Expr {
    }

    /** 布尔字面量 {@code true}/{@code false}。 */
    record Bool(boolean value, int pos) implements Expr {
    }

    /** 一元运算：{@code !}、{@code ~}、{@code -}。 */
    record Unary(String op, Expr operand, int pos) implements Expr {
    }

    /** 二元运算。 */
    record Binary(String op, Expr left, Expr right, int pos) implements Expr {
    }

    /** 三元运算：{@code cond ? whenTrue : whenFalse}（右结合）。 */
    record Ternary(Expr cond, Expr whenTrue, Expr whenFalse, int pos) implements Expr {
    }

    /** 函数调用：{@code name(args)}。 */
    record Call(String name, List<Expr> args, int pos) implements Expr {
    }

    /** 用 visitor 遍历此节点（默认实现按节点类型分发）。 */
    default <R> R accept(ExprVisitor<R> v) {
        return switch (this) {
            case Number n -> v.number(n.value());
            case Ident id -> v.ident(id.name());
            case Str s -> v.string(s.value());
            case Bool b -> v.bool(b.value());
            case Unary u -> v.unary(u.op(), u.operand().accept(v));
            case Binary b -> v.binary(b.op(), b.left().accept(v), b.right().accept(v));
            case Ternary t -> v.ternary(t.cond().accept(v), t.whenTrue().accept(v), t.whenFalse().accept(v));
            case Call c -> v.call(c.name(), c.args().stream().map(a -> a.accept(v)).toList());
        };
    }
}
