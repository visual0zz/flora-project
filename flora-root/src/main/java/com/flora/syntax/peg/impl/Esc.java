package com.flora.syntax.peg.impl;

/**
 * 字面量与字符类中的转义序列解析工具。只接受已知转义；未知转义在元解析期即被拒绝（不静默猜测）。
 */
final class Esc {

    private Esc() {}

    /** 转义序列是否为已知且合法（含 backslash-u 4 位十六进制 / backslash-x 2 位十六进制的位数校验）。 */
    static boolean isKnown(String s, int i) {
        if (i + 1 >= s.length()) return false;
        char c = s.charAt(i + 1);
        return switch (c) {
            case 'n', 't', 'r', 'f', 'b', '\\', '\'', '"', '/', ']', '[', '-' -> true;
            case 'u' -> isHex(s, i + 2, 4);
            case 'x' -> isHex(s, i + 2, 2);
            default -> false;
        };
    }

    /** 返回从 i（指向反斜杠）开始的转义序列总长度（含反斜杠）。调用方应先用 {@link #isKnown} 校验。 */
    static int escapeLength(String s, int i) {
        char c = s.charAt(i + 1);
        return switch (c) {
            case 'u' -> 6;  // backslash-u + 4 位十六进制
            case 'x' -> 4;  // backslash-x + 2 位十六进制
            default -> 2;
        };
    }

    /** 解码从 i（指向反斜杠）开始的转义序列，返回码点。调用方应先用 {@link #isKnown} 校验。 */
    static int decode(String s, int i) {
        char c = s.charAt(i + 1);
        return switch (c) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case 'f' -> '\f';
            case 'b' -> '\b';
            case '\\' -> '\\';
            case '\'' -> '\'';
            case '"' -> '"';
            case '/' -> '/';
            case ']' -> ']';
            case '[' -> '[';
            case '-' -> '-';
            case 'u' -> Integer.parseInt(s.substring(i + 2, i + 6), 16);
            case 'x' -> Integer.parseInt(s.substring(i + 2, i + 4), 16);
            default -> throw new IllegalArgumentException("未知的转义序列 \\" + c);
        };
    }

    private static boolean isHex(String s, int from, int len) {
        if (from + len > s.length()) return false;
        for (int k = from; k < from + len; k++) {
            char c = s.charAt(k);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }
}
