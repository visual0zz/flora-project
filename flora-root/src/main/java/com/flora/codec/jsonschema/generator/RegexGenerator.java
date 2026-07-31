package com.flora.codec.jsonschema.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单正则子集逆向生成器。
 * <p>支持：字面字符、{@code .}、字符类 {@code [a-z]}/{@code [^...]}、{@code \d}/{@code \w}/{@code \s}、
 * 量词 {@code *} {@code +} {@code ?} {@code {n}} {@code {n,m}} {@code {n,}}、
 * 分组与交替 {@code (a|b)}；锚 {@code ^}/{@code $} 忽略。
 * 遇到不支持的结构返回 {@code null}，由调用方回退随机字符串。</p>
 */
final class RegexGenerator {

    private final RandomSupport random;

    RegexGenerator(RandomSupport random) {
        this.random = random;
    }

    /** @return 匹配 pattern 的随机字符串；不支持返回 null */
    String generate(String pattern) {
        try {
            Parser p = new Parser(pattern);
            List<Atom> atoms = p.parseSequence();
            if (!p.isAtEnd()) {
                return null; // 未消费完 → 不支持
            }
            StringBuilder sb = new StringBuilder();
            for (Atom atom : atoms) {
                atom.append(sb);
            }
            return sb.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ── 原子模型 ──

    private interface Atom {
        void append(StringBuilder sb);
    }

    private abstract static class BaseAtom implements Atom {
        private int min;
        private int max; // -1 表示不限

        void quantifier(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public void append(StringBuilder sb) {
            int count = count();
            for (int i = 0; i < count; i++) {
                emit(sb);
            }
        }

        private int count() {
            if (max < 0) {
                return min + randomOf(0, 3); // {n,} 上限放宽
            }
            if (max == min) {
                return min;
            }
            return min + randomOf(0, max - min);
        }

        abstract void emit(StringBuilder sb);

        private int randomOf(int lo, int hi) {
            return lo + (int) (Math.random() * (hi - lo + 1));
        }
    }

    private static final class LiteralAtom extends BaseAtom {
        private final char c;

        LiteralAtom(char c) {
            this.c = c;
        }

        @Override
        void emit(StringBuilder sb) {
            sb.append(c);
        }
    }

    private final class CharClassAtom extends BaseAtom {
        private final List<Character> chars;

        CharClassAtom(List<Character> chars) {
            this.chars = chars;
        }

        @Override
        void emit(StringBuilder sb) {
            sb.append(chars.get(random.random().nextInt(chars.size())));
        }
    }

    private final class GroupAtom extends BaseAtom {
        private final List<List<Atom>> alternatives;

        GroupAtom(List<List<Atom>> alternatives) {
            this.alternatives = alternatives;
        }

        @Override
        void emit(StringBuilder sb) {
            List<Atom> branch = alternatives.get(random.random().nextInt(alternatives.size()));
            for (Atom atom : branch) {
                atom.append(sb);
            }
        }
    }

    // ── 解析器 ──

    private final class Parser {
        private final String pattern;
        private int pos;

        Parser(String pattern) {
            this.pattern = pattern;
        }

        boolean isAtEnd() {
            return pos >= pattern.length();
        }

        List<Atom> parseSequence() {
            List<Atom> atoms = new ArrayList<>();
            while (!isAtEnd()) {
                char c = pattern.charAt(pos);
                if (c == '|' || c == ')') {
                    break;
                }
                if (c == '^' || c == '$') {
                    pos++; // 锚忽略
                    continue;
                }
                Atom atom = parseAtom();
                atoms.add(atom);
            }
            return atoms;
        }

        private Atom parseAtom() {
            char c = pattern.charAt(pos);
            BaseAtom atom;
            if (c == '.') {
                pos++;
                atom = new CharClassAtom(allChars());
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
                atom = new LiteralAtom(c);
            }
            parseQuantifier(atom);
            return atom;
        }

        private BaseAtom parseEscape() {
            char c = pattern.charAt(pos);
            pos++;
            return switch (c) {
                case 'd' -> new CharClassAtom(digits());
                case 'w' -> new CharClassAtom(alnum());
                case 's' -> new CharClassAtom(List.of(' '));
                default -> new LiteralAtom(c);
            };
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
            }
            if (negate) {
                // 简化为排除：若字母表差集为空则用原集合
                List<Character> diff = new ArrayList<>();
                for (char c : allChars()) {
                    if (!chars.contains(c)) {
                        diff.add(c);
                    }
                }
                if (!diff.isEmpty()) {
                    chars = diff;
                }
            }
            if (chars.isEmpty()) {
                throw new IllegalArgumentException("空字符类");
            }
            return new CharClassAtom(chars);
        }

        private BaseAtom parseGroup() {
            List<List<Atom>> alternatives = new ArrayList<>();
            while (true) {
                List<Atom> branch = parseSequence();
                alternatives.add(branch);
                if (!isAtEnd() && pattern.charAt(pos) == '|') {
                    pos++;
                } else {
                    break;
                }
            }
            if (!isAtEnd() && pattern.charAt(pos) == ')') {
                pos++;
            }
            return new GroupAtom(alternatives);
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
                            return; // 非法量词，忽略
                        }
                        pos = close + 1;
                    }
                }
                default -> {
                }
            }
        }
    }

    // ── 字符集 ──

    private static List<Character> digits() {
        List<Character> l = new ArrayList<>();
        for (char c = '0'; c <= '9'; c++) {
            l.add(c);
        }
        return l;
    }

    private static List<Character> alnum() {
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
        return l;
    }

    private static List<Character> allChars() {
        List<Character> l = new ArrayList<>();
        for (char c = 'a'; c <= 'z'; c++) {
            l.add(c);
        }
        return l;
    }
}
