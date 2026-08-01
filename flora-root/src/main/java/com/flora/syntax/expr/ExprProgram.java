package com.flora.syntax.expr;

import java.util.List;

/**
 * 编译产物：持有表达式 AST，可用不同 {@link Semantics} 反复执行。
 * <p>本身不带类型参数——{@link #evaluate(Semantics)} 时才绑定语义结果类型 T，
 * 因此同一 program 可用不同语义（如整数求值、字符串拼接）执行。</p>
 */
public final class ExprProgram {

    private final Expr ast;

    ExprProgram(Expr ast) {
        this.ast = ast;
    }

    /** 用给定语义执行表达式，返回结果。 */
    public <T> T evaluate(Semantics<T> semantics) {
        return eval(ast, semantics);
    }

    /** 编译时生成的 AST（公开，供自定义遍历/翻译）。 */
    public Expr ast() {
        return ast;
    }

    private static <T> T eval(Expr node, Semantics<T> s) {
        return switch (node) {
            case Expr.Number n -> s.number(n.value());
            case Expr.Ident id -> s.ident(id.name());
            case Expr.Str str -> s.string(str.value());
            case Expr.Bool b -> s.bool(b.value());
            case Expr.Unary u -> s.unary(u.op(), eval(u.operand(), s));
            case Expr.Binary b -> s.binary(b.op(), eval(b.left(), s), () -> eval(b.right(), s));
            case Expr.Ternary t -> s.ternary(eval(t.cond(), s),
                    () -> eval(t.whenTrue(), s), () -> eval(t.whenFalse(), s));
            case Expr.Call c -> s.call(c.name(), c.args().stream().map(a -> eval(a, s)).toList());
        };
    }

    @Override
    public String toString() {
        return print(ast);
    }

    /** 打印中缀形式（括号完整，供调试）。 */
    private static String print(Expr node) {
        return switch (node) {
            case Expr.Number n -> n.value();
            case Expr.Ident id -> id.name();
            case Expr.Str str -> "\"" + str.value() + "\"";
            case Expr.Bool b -> String.valueOf(b.value());
            case Expr.Unary u -> u.op() + print(u.operand());
            case Expr.Binary b -> "(" + print(b.left()) + " " + b.op() + " " + print(b.right()) + ")";
            case Expr.Ternary t -> "(" + print(t.cond()) + " ? " + print(t.whenTrue())
                    + " : " + print(t.whenFalse()) + ")";
            case Expr.Call c -> c.name() + "(" + joinArgs(c.args()) + ")";
        };
    }

    private static String joinArgs(List<Expr> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(print(args.get(i)));
        }
        return sb.toString();
    }
}
