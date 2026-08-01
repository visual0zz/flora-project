package com.flora.mock.regex.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * 正则字符集工具：为简写类、通配符、取反提供候选字符集合。
 */
public final class RegexCharSets {

    private RegexCharSets() {
    }

    public static List<Character> digits() {
        List<Character> l = new ArrayList<>();
        for (char c = '0'; c <= '9'; c++) {
            l.add(c);
        }
        return l;
    }

    public static List<Character> alnum() {
        List<Character> l = new ArrayList<>();
        for (char c = 'a'; c <= 'z'; c++) {
            l.add(c);
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            l.add(c);
        }
        for (char c = '0'; c <= '9'; c++) {
            l.add(c);
        }
        l.add('_');
        return l;
    }

    public static List<Character> whitespace() {
        return List.of(' ', '\t', '\n', '\r', '\f');
    }

    /** 可打印 ASCII：'!'..'~'。 */
    public static List<Character> allChars() {
        List<Character> l = new ArrayList<>();
        for (char c = '!'; c <= '~'; c++) {
            l.add(c);
        }
        return l;
    }

    /** 从可打印 ASCII 中排除给定集合（取反语义）。 */
    public static List<Character> complement(List<Character> excluded) {
        List<Character> diff = new ArrayList<>();
        for (char c = '!'; c <= '~'; c++) {
            if (!excluded.contains(c)) {
                diff.add(c);
            }
        }
        if (diff.isEmpty()) {
            throw new IllegalArgumentException("取反后为空");
        }
        return diff;
    }
}
