package com.flora.root.syntax.expr.impl;

import com.flora.root.syntax.common.exceptions.SyntaxException;

import java.util.HashMap;
import java.util.Map;

/**
 * C/Java 风格运算符优先级表。
 * <p>运算符集合固定（不支持用户扩展）；从低到高优先级：
 * {@code ||} &lt; {@code &&} &lt; {@code |} &lt; {@code ^} &lt; {@code &}
 * &lt; {@code == !=} &lt; {@code < <= > >=} &lt; {@code << >> >>>}
 * &lt; {@code + -} &lt; {@code * / %} &lt; 一元 &lt; 括号 &lt; 三元（最低，见 parser）。</p>
 */
public final class OpPrecedence {

    /** 一元运算符集合。 */
    static final String[] UNARY = {"!", "~", "-"};

    /** 运算符 → 优先级（数字越大越先结合）。 */
    private static final Map<String, Integer> PRECEDENCE = new HashMap<>();

    static {
        PRECEDENCE.put("||", 1);
        PRECEDENCE.put("&&", 2);
        PRECEDENCE.put("|", 3);
        PRECEDENCE.put("^", 4);
        PRECEDENCE.put("&", 5);
        PRECEDENCE.put("==", 6);
        PRECEDENCE.put("!=", 6);
        PRECEDENCE.put("<", 7);
        PRECEDENCE.put("<=", 7);
        PRECEDENCE.put(">", 7);
        PRECEDENCE.put(">=", 7);
        PRECEDENCE.put("<<", 8);
        PRECEDENCE.put(">>", 8);
        PRECEDENCE.put(">>>", 8);
        PRECEDENCE.put("+", 9);
        PRECEDENCE.put("-", 9);
        PRECEDENCE.put("*", 10);
        PRECEDENCE.put("/", 10);
        PRECEDENCE.put("%", 10);
    }

    private OpPrecedence() {
    }

    /** 是否二元运算符。 */
    public static boolean isBinary(String op) {
        return PRECEDENCE.containsKey(op);
    }

    /** 是否一元运算符。 */
    public static boolean isUnary(String op) {
        for (String u : UNARY) {
            if (u.equals(op)) {
                return true;
            }
        }
        return false;
    }

    /** 运算符优先级；非二元运算符抛异常。 */
    public static int level(String op) {
        Integer level = PRECEDENCE.get(op);
        if (level == null) {
            throw SyntaxException.at(-1, "未知运算符: " + op);
        }
        return level;
    }
}
