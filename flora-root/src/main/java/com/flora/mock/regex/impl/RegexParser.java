package com.flora.mock.regex.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 正则递归下降解析器：把模式解析为原子序列。
 * <p>支持字面量、{@code .}、字符类、简写类、转义、Unicode 属性、量词、分组与交替；
 * 锚 {@code ^}/{@code $} 忽略；不支持的语法抛 {@link IllegalArgumentException}。</p>
 */
public final class RegexParser {

    private final String pattern;
    private final RandomGenerator random;
    private int pos;

    public RegexParser(String pattern, RandomGenerator random) {
        this.pattern = pattern;
        this.random = random;
    }

    public boolean isAtEnd() {
        return pos >= pattern.length();
    }

    public List<RegexAtom> parseSequence() {
        List<RegexAtom> atoms = new ArrayList<>();
        while (!isAtEnd()) {
            char c = pattern.charAt(pos);
            if (c == '|' || c == ')') {
                break;
            }
            if (c == '^' || c == '$') {
                pos++; // 锚忽略
                continue;
            }
            RegexAtom atom = parseAtom();
            atoms.add(atom);
        }
        return atoms;
    }

    private RegexAtom parseAtom() {
        char c = pattern.charAt(pos);
        BaseAtom atom;
        if (c == '.') {
            pos++;
            atom = new CharClassAtom(RegexCharSets.allChars(), random);
        } else if (c == '\\') {
            pos++;
            atom = parseEscape();
        } else if (c == '[') {
            pos++;
            atom = parseCharClass();
        } else if (c == '(') {
            pos++;
            atom = parseGroup();
        } else {
            pos++;
            atom = new LiteralAtom(c, random);
        }
        parseQuantifier(atom);
        return atom;
    }

    private BaseAtom parseEscape() {
        char c = pattern.charAt(pos);
        pos++;
        return switch (c) {
            case 'd' -> new CharClassAtom(RegexCharSets.digits(), random);
            case 'w' -> new CharClassAtom(RegexCharSets.alnum(), random);
            case 's' -> new CharClassAtom(RegexCharSets.whitespace(), random);
            case 'D' -> new CharClassAtom(RegexCharSets.complement(RegexCharSets.digits()), random);
            case 'W' -> new CharClassAtom(RegexCharSets.complement(RegexCharSets.alnum()), random);
            case 'S' -> new CharClassAtom(RegexCharSets.complement(RegexCharSets.whitespace()), random);
            case 't' -> new LiteralAtom('\t', random);
            case 'n' -> new LiteralAtom('\n', random);
            case 'r' -> new LiteralAtom('\r', random);
            case 'f' -> new LiteralAtom('\f', random);
            case '0' -> new LiteralAtom('\0', random);
            case 'p', 'P' -> parseUnicodeProperty(c == 'P');
            case '1', '2', '3', '4', '5', '6', '7', '8', '9' ->
                    throw new IllegalArgumentException("反向引用不支持");
            default -> new LiteralAtom(c, random);
        };
    }

    private BaseAtom parseUnicodeProperty(boolean negate) {
        if (isAtEnd() || pattern.charAt(pos) != '{') {
            throw new IllegalArgumentException("缺少 {");
        }
        int close = pattern.indexOf('}', pos);
        if (close < 0) {
            throw new IllegalArgumentException("缺少 }");
        }
        String name = pattern.substring(pos + 1, close);
        int property = UnicodePropertyRanges.propertyOf(name);
        if (property < 0) {
            throw new IllegalArgumentException("未知属性: " + name);
        }
        pos = close + 1;
        return new UnicodeClassAtom(property, negate, random);
    }

    private BaseAtom parseCharClass() {
        boolean negate = false;
        if (!isAtEnd() && pattern.charAt(pos) == '^') {
            negate = true;
            pos++;
        }
        List<Character> chars = new ArrayList<>();
        while (!isAtEnd() && pattern.charAt(pos) != ']') {
            char c = pattern.charAt(pos);
            pos++;
            if (c == '\\') {
                addEscape(chars);
                continue;
            }
            // 范围展开：x-y
            if (!isAtEnd() && pattern.charAt(pos) == '-'
                    && pos + 1 < pattern.length() && pattern.charAt(pos + 1) != ']') {
                pos++; // 跳过 -
                char end = pattern.charAt(pos);
                pos++;
                for (char ch = c; ch <= end; ch++) {
                    chars.add(ch);
                }
            } else {
                chars.add(c);
            }
        }
        if (!isAtEnd()) {
            pos++; // 跳过 ]
        } else {
            throw new IllegalArgumentException("字符类未闭合");
        }
        if (negate) {
            List<Character> diff = new ArrayList<>(RegexCharSets.complement(new ArrayList<>(chars)));
            chars = diff;
        }
        if (chars.isEmpty()) {
            throw new IllegalArgumentException("空字符类");
        }
        return new CharClassAtom(chars, random);
    }

    /** 字符类内的转义：\d \w \s \D \W \S、\t \n \r \f \0，其余作字面量。 */
    private void addEscape(List<Character> chars) {
        if (isAtEnd()) {
            throw new IllegalArgumentException("转义不完整");
        }
        char c = pattern.charAt(pos);
        pos++;
        switch (c) {
            case 'd' -> chars.addAll(RegexCharSets.digits());
            case 'w' -> chars.addAll(RegexCharSets.alnum());
            case 's' -> chars.addAll(RegexCharSets.whitespace());
            case 'D' -> chars.addAll(RegexCharSets.complement(RegexCharSets.digits()));
            case 'W' -> chars.addAll(RegexCharSets.complement(RegexCharSets.alnum()));
            case 'S' -> chars.addAll(RegexCharSets.complement(RegexCharSets.whitespace()));
            case 't' -> chars.add('\t');
            case 'n' -> chars.add('\n');
            case 'r' -> chars.add('\r');
            case 'f' -> chars.add('\f');
            case '0' -> chars.add('\0');
            case 'p', 'P' -> throw new IllegalArgumentException("字符类内不支持属性");
            default -> chars.add(c);
        }
    }

    private BaseAtom parseGroup() {
        // 前缀：?: 非捕获组（与捕获组同处理）；(?= (?! (?<= (?< 为不支持结构
        if (!isAtEnd() && pattern.charAt(pos) == '?') {
            if (pos + 1 < pattern.length() && pattern.charAt(pos + 1) == ':') {
                pos += 2; // (?: 非捕获组
            } else {
                throw new IllegalArgumentException("不支持的分组前缀");
            }
        }
        List<List<RegexAtom>> alternatives = new ArrayList<>();
        while (true) {
            List<RegexAtom> branch = parseSequence();
            alternatives.add(branch);
            if (!isAtEnd() && pattern.charAt(pos) == '|') {
                pos++;
            } else {
                break;
            }
        }
        if (!isAtEnd() && pattern.charAt(pos) == ')') {
            pos++;
        } else {
            throw new IllegalArgumentException("分组未闭合");
        }
        return new GroupAtom(alternatives, random);
    }

    private void parseQuantifier(BaseAtom atom) {
        if (isAtEnd()) {
            return;
        }
        char c = pattern.charAt(pos);
        switch (c) {
            case '*' -> {
                pos++;
                atom.quantifier(0, -1);
            }
            case '+' -> {
                pos++;
                atom.quantifier(1, -1);
            }
            case '?' -> {
                pos++;
                atom.quantifier(0, 1);
            }
            case '{' -> {
                int close = pattern.indexOf('}', pos);
                if (close > 0) {
                    String body = pattern.substring(pos + 1, close);
                    String[] parts = body.split(",", -1);
                    try {
                        int min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
                        if (parts.length == 1) {
                            atom.quantifier(min, min);
                        } else {
                            int max = parts[1].isEmpty() ? -1 : Integer.parseInt(parts[1]);
                            atom.quantifier(min, max);
                        }
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("非法量词: " + body);
                    }
                    pos = close + 1;
                } else {
                    throw new IllegalArgumentException("量词未闭合");
                }
            }
            default -> {
            }
        }
        if (atom.min() < 0 || (atom.max() >= 0 && atom.max() > BaseAtom.MAX_REPEAT)
                || (atom.max() < 0 && atom.min() > BaseAtom.MAX_REPEAT)) {
            throw new IllegalArgumentException("重复上限超阈值");
        }
        // 懒惰量词后缀 ? 忽略
        if (!isAtEnd() && pattern.charAt(pos) == '?') {
            pos++;
        }
    }
}
