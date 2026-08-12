package com.flora.root.syntax.expr;

import com.flora.root.syntax.common.exceptions.SyntaxException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 表达式语义：定义 AST 中每种节点/运算符的实际运算。
 * <p>用户通过 {@link #builder()} 注入 lambda 来定义自己 DSL 中每个运算符的行为；
 * 未注入的运算符在 {@link ExprProgram#evaluate(Semantics)} 时抛 {@link SyntaxException}。
 * 二元运算的右操作数以 {@link Supplier} 惰性传入，用户可决定是否消费以实现短路
 * （如 {@code &&}/{@code ||}）。内置 {@link #intArithmetic()} 提供开箱即用的
 * 整数四则/位运算/比较/逻辑语义（含短路）。</p>
 *
 * <pre>{@code
 * Semantics<Integer> s = Semantics.<Integer>builder()
 *         .onNumber(Integer::parseInt)
 *         .onBinary("+", (a, right) -> a + right.get())
 *         .build();
 * }</pre>
 */
public interface Semantics<T> {

    /** 数字字面量语义。 */
    T number(String value);

    /** 标识符语义。 */
    T ident(String name);

    /** 字符串字面量语义。 */
    T string(String value);

    /** 布尔字面量语义。 */
    T bool(boolean value);

    /** 一元运算语义（op: {@code !}/{@code ~}/{@code -}）。 */
    T unary(String op, T operand);

    /**
     * 二元运算语义（op: C/Java 运算符）。
     * 右操作数以 {@link Supplier} 惰性传入：用户 lambda 可决定是否调用
     * {@code right.get()} 以实现短路（如 {@code &&}/{@code ||}）。
     */
    T binary(String op, T left, Supplier<T> rightFn);

    /** 三元运算语义（cond 与两个分支，分支惰性传入以实现短路）。 */
    T ternary(T cond, Supplier<T> whenTrue, Supplier<T> whenFalse);

    /** 函数调用语义。 */
    T call(String name, List<T> args);

    /** 开箱即用的整数语义：四则/位运算/比较/逻辑（含短路），一元负号与按位非。 */
    static Semantics<Integer> intArithmetic() {
        return Semantics.<Integer>builder()
                .onNumber(Integer::parseInt)
                .onIdent(name -> {
                    throw SyntaxException.at(-1, "未知标识符: " + name);
                })
                .onString(value -> {
                    throw SyntaxException.at(-1, "数值语义不接受字符串: " + value);
                })
                .onBool(value -> value ? 1 : 0)
                .onUnary("-", x -> -x)
                .onUnary("!", x -> x == 0 ? 1 : 0)
                .onUnary("~", x -> ~x)
                .onBinary("+", (a, right) -> a + right.get())
                .onBinary("-", (a, right) -> a - right.get())
                .onBinary("*", (a, right) -> a * right.get())
                .onBinary("/", (a, right) -> a / right.get())
                .onBinary("%", (a, right) -> a % right.get())
                .onBinary("<<", (a, right) -> a << right.get())
                .onBinary(">>", (a, right) -> a >> right.get())
                .onBinary(">>>", (a, right) -> a >>> right.get())
                .onBinary("&", (a, right) -> a & right.get())
                .onBinary("^", (a, right) -> a ^ right.get())
                .onBinary("|", (a, right) -> a | right.get())
                .onBinary("&&", (a, right) -> a != 0 ? right.get() : 0)          // 短路
                .onBinary("||", (a, right) -> a != 0 ? 1 : right.get())          // 短路
                .onBinary("==", (a, right) -> a.equals(right.get()) ? 1 : 0)
                .onBinary("!=", (a, right) -> a.equals(right.get()) ? 0 : 1)
                .onBinary("<", (a, right) -> a < right.get() ? 1 : 0)
                .onBinary("<=", (a, right) -> a <= right.get() ? 1 : 0)
                .onBinary(">", (a, right) -> a > right.get() ? 1 : 0)
                .onBinary(">=", (a, right) -> a >= right.get() ? 1 : 0)
                .onTernary((cond, t, f) -> cond != 0 ? t.get() : f.get())
                .build();
    }

    /** 创建语义 builder，链式注入 lambda。 */
    static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /** 语义 builder：链式注入每个运算符/字面量的 lambda。 */
    final class Builder<T> {
        private final Map<String, UnaryOp<T>> unaryOps = new HashMap<>();
        private final Map<String, LazyBinaryOp<T>> binaryOps = new HashMap<>();
        private TernaryOp<T> ternaryOp = (_, _, _) -> {
            throw missing("三元运算");
        };
        private Function<String, T> numberFn = _ -> {
            throw missing("数字字面量");
        };
        private Function<String, T> identFn = _ -> {
            throw missing("标识符");
        };
        private Function<String, T> stringFn = _ -> {
            throw missing("字符串字面量");
        };
        private Function<Boolean, T> boolFn = _ -> {
            throw missing("布尔字面量");
        };
        private CallOp<T> callOp = (_, _) -> {
            throw missing("函数调用");
        };

        public Builder<T> onNumber(Function<String, T> fn) {
            this.numberFn = fn;
            return this;
        }

        public Builder<T> onIdent(Function<String, T> fn) {
            this.identFn = fn;
            return this;
        }

        public Builder<T> onString(Function<String, T> fn) {
            this.stringFn = fn;
            return this;
        }

        public Builder<T> onBool(Function<Boolean, T> fn) {
            this.boolFn = fn;
            return this;
        }

        public Builder<T> onUnary(String op, UnaryOp<T> fn) {
            unaryOps.put(op, fn);
            return this;
        }

        public Builder<T> onBinary(String op, LazyBinaryOp<T> fn) {
            binaryOps.put(op, fn);
            return this;
        }

        public Builder<T> onTernary(TernaryOp<T> fn) {
            this.ternaryOp = fn;
            return this;
        }

        public Builder<T> onCall(CallOp<T> fn) {
            this.callOp = fn;
            return this;
        }

        public Semantics<T> build() {
            return new Semantics<>() {
                @Override
                public T number(String value) {
                    return numberFn.apply(value);
                }

                @Override
                public T ident(String name) {
                    return identFn.apply(name);
                }

                @Override
                public T string(String value) {
                    return stringFn.apply(value);
                }

                @Override
                public T bool(boolean value) {
                    return boolFn.apply(value);
                }

                @Override
                public T unary(String op, T operand) {
                    UnaryOp<T> fn = unaryOps.get(op);
                    if (fn == null) {
                        throw missing("一元运算符 " + op);
                    }
                    return fn.apply(operand);
                }

                @Override
                public T binary(String op, T left, Supplier<T> rightFn) {
                    LazyBinaryOp<T> fn = binaryOps.get(op);
                    if (fn == null) {
                        throw missing("二元运算符 " + op);
                    }
                    return fn.apply(left, rightFn);
                }

                @Override
                public T ternary(T cond, Supplier<T> whenTrue, Supplier<T> whenFalse) {
                    return ternaryOp.apply(cond, whenTrue, whenFalse);
                }

                @Override
                public T call(String name, List<T> args) {
                    return callOp.apply(name, args);
                }
            };
        }

        private static SyntaxException missing(String what) {
            return new SyntaxException("语义中未注入 " + what);
        }
    }

    /** 一元运算：{@code T -> T}。 */
    @FunctionalInterface
    interface UnaryOp<T> {
        T apply(T operand);
    }

    /** 惰性二元运算：左值 + 右值供给器。 */
    @FunctionalInterface
    interface LazyBinaryOp<T> {
        T apply(T left, Supplier<T> rightFn);
    }

    /** 三元运算：条件 + 两个惰性分支。 */
    @FunctionalInterface
    interface TernaryOp<T> {
        T apply(T cond, Supplier<T> whenTrue, Supplier<T> whenFalse);
    }

    /** 函数调用：名称 + 参数列表。 */
    @FunctionalInterface
    interface CallOp<T> {
        T apply(String name, List<T> args);
    }
}
