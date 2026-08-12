package com.flora.root.syntax.expr;

import com.flora.root.syntax.expr.impl.OpPrecedence;

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

    /** 打印中缀形式（按优先级去冗余括号，供人类阅读）。 */
    public String prettyPrint() {
        return pretty(ast, 0);
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

    /** 去冗余括号打印：子节点优先级低于父节点或结构复杂时加括号。 */
    private static String pretty(Expr node, int parentLevel) {
        return switch (node) {
            case Expr.Number n -> n.value();
            case Expr.Ident id -> id.name();
            case Expr.Str str -> "\"" + str.value() + "\"";
            case Expr.Bool b -> String.valueOf(b.value());
            case Expr.Unary u -> {
                // 传 MAX_VALUE：操作数若是二元/三元则必然加括号（如 -(1+2)），一元/叶子不加
                yield u.op() + pretty(u.operand(), Integer.MAX_VALUE);
            }
            case Expr.Binary b -> {
                int level = OpPrecedence.level(b.op());
                String left = pretty(b.left(), level);
                String right = pretty(b.right(), level);
                // 右子节点同级时也加括号（保持左结合语义，如 a-(b-c)）
                String rightPart = rightChildNeedsParens(b) ? "(" + right + ")" : right;
                String expr = left + " " + b.op() + " " + rightPart;
                yield level < parentLevel ? "(" + expr + ")" : expr;
            }
            case Expr.Ternary t -> {
                String expr = pretty(t.cond(), 0) + " ? " + pretty(t.whenTrue(), 0)
                        + " : " + pretty(t.whenFalse(), 0);
                yield parentLevel > 0 ? "(" + expr + ")" : expr;
            }
            case Expr.Call c -> c.name() + "(" + joinArgsPretty(c.args()) + ")";
        };
    }

    /** 右子节点是否需括号：右子是同优先级的二元（左结合下需保序）。 */
    private static boolean rightChildNeedsParens(Expr.Binary b) {
        if (b.right() instanceof Expr.Binary rb) {
            int leftLevel = OpPrecedence.level(b.op());
            int rightLevel = OpPrecedence.level(rb.op());
            return rightLevel <= leftLevel;
        }
        return false;
    }

    private static String joinArgsPretty(List<Expr> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(pretty(args.get(i), 0));
        }
        return sb.toString();
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
