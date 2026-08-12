package com.flora.crypto.core.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * 密码学组件 DSL 表达式解析器（语法与旧 core 一致）。
 * <pre>
 * 表达式 = 裸名 | 裸名(表达式, ...) | 字面量
 * 字面量 = integer:数字 | float:小数 | string:文本 | bytes:十六进制串
 * 裸名   = [不含括号、逗号和冒号的字符串]
 * </pre>
 * 解析结果：裸名 → {@link String}，{@code name(args...)} → {@link Invocation}，
 * 字面量 → 对应 Java 类型（{@link Integer} / {@link Double} / {@link String} / {@code byte[]}）。
 */
public final class DslParser {

    public record Invocation(String name, Object[] args) {}

    private DslParser() {}

    public static Object parse(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("DSL expression cannot be empty");
        }
        expr = expr.trim();
        int open = findTopLevelParen(expr);
        if (open >= 0) {
            int close = findMatchingCloseParen(expr, open);
            String name = expr.substring(0, open).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Missing algorithm name before '(' in: " + expr);
            }
            String inner = expr.substring(open + 1, close).trim();
            Object[] args = inner.isEmpty() ? new Object[0] : splitAndResolve(inner);
            return new Invocation(name, args);
        }
        if (expr.contains(":")) {
            return parseLiteral(expr);
        }
        return expr;
    }

    private static int findTopLevelParen(String expr) {
        for (int i = 0; i < expr.length(); i++) {
            if (expr.charAt(i) == '(') return i;
        }
        return -1;
    }

    private static int findMatchingCloseParen(String expr, int open) {
        int depth = 0;
        for (int i = open; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        throw new IllegalArgumentException("Unbalanced parentheses in DSL expression: " + expr);
    }

    private static Object[] splitAndResolve(String s) {
        List<Object> result = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                result.add(parse(s.substring(start, i).trim()));
                start = i + 1;
            }
        }
        result.add(parse(s.substring(start).trim()));
        return result.toArray();
    }

    private static Object parseLiteral(String expr) {
        int colon = expr.indexOf(':');
        String prefix = expr.substring(0, colon).trim();
        String value = expr.substring(colon + 1).trim();
        return switch (prefix) {
            case "integer" -> Integer.parseInt(value);
            case "float" -> Double.parseDouble(value);
            case "string" -> value;
            case "bytes" -> decodeHex(value);
            default -> expr;
        };
    }

    private static byte[] decodeHex(String hex) {
        if (hex.isEmpty()) return new byte[0];
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Odd-length hex string: " + hex);
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((hexDigit(hex.charAt(i)) << 4) | hexDigit(hex.charAt(i + 1)));
        }
        return out;
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        throw new IllegalArgumentException("Invalid hex char: " + c);
    }
}
