package com.flora.tangle;

import java.util.HashSet;
import java.util.Set;

/**
 * 生成短小且合法的类名/成员名标识符。
 *
 * <p>混淆器需要把原本冗长的名字替换成难以阅读、彼此相似的短名字。这里用固定字母表把自增计数器
 * 编成标识符，并保证全局唯一且不落入 Java 关键字。
 */
public final class NameGenerator {

    /** 标识符可用的字符（不含数字，以避免与数值混淆，且首字符恒为字母/$/_）。 */
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZ$_";

    /** Java 关键字/保留字：生成的名字必须避开它们。 */
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "exports", "extends",
            "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
            "int", "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "var", "void", "volatile", "while", "module",
            "requires", "opens", "to", "with", "provides", "uses", "true", "false", "null");

    private final Set<String> used = new HashSet<>();
    private long counter = 0;

    /** 预留一些名字（例如已有的类名/成员名），避免生成的结果与之冲突。 */
    public void reserve(String name) {
        used.add(name);
    }

    /** 取下一个唯一且非关键字的标识符。 */
    public String next() {
        String name;
        do {
            name = encode(counter++);
        } while (used.contains(name) || KEYWORDS.contains(name));
        used.add(name);
        return name;
    }

    /** 把计数器值按字母表编码成标识符（类似 26 进制，但基数为字母表长度）。 */
    private static String encode(long value) {
        StringBuilder sb = new StringBuilder();
        long v = value;
        do {
            int digit = (int) (v % ALPHABET.length());
            sb.append(ALPHABET.charAt(digit));
            v /= ALPHABET.length();
        } while (v > 0);
        return sb.reverse().toString();
    }
}
