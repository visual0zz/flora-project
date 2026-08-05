package com.flora.syntax.peg.impl;

/** 字面量与字符类中的转义序列解析工具。 */
final class Esc {

    private Esc() {}

    /** 返回从 i（指向反斜杠）开始的转义序列总长度（含反斜杠）。 */
    static int escapeLength(String s, int i) {
        char c = s.charAt(i + 1);
        return switch (c) {
            case 'u' -> 6;  // backslash-u 形式（4 位十六进制）；源码注释勿写反斜杠+u，会触发 javac Unicode 转义
            case 'x' -> 4;  // \xHH
            default -> 2;
        };
    }

    /** 解码从 i（指向反斜杠）开始的转义序列，返回码点。 */
    static int decode(String s, int i) {
        char c = s.charAt(i + 1);
        return switch (c) {
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'r' -> '\r';
            case '\\' -> '\\';
            case '\'' -> '\'';
            case '"' -> '"';
            case '/' -> '/';
            case ']' -> ']';
            case '[' -> '[';
            case '-' -> '-';
            case 'u' -> Integer.parseInt(s.substring(i + 2, i + 6), 16);
            case 'x' -> Integer.parseInt(s.substring(i + 2, i + 4), 16);
            default -> c;
        };
    }
}
