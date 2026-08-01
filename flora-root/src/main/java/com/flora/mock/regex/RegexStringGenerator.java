package com.flora.mock.regex;

import com.flora.tag.ThreadFragile;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * 正则表达式字符串生成器：构造匹配给定正则的随机字符串。
 * <p>支持语法：字面量、{@code .}（可打印 ASCII）、字符类 {@code [a-z]}/{@code [^...]}、
 * {@code \d} {@code \w} {@code \s} 及其取反 {@code \D} {@code \W} {@code \S}、
 * 转义 {@code \t} {@code \n} {@code \r} {@code \f} {@code \0}、
 * Unicode 属性 {@code \p{...}}（如 {@code \p{L}} {@code \p{Nd}}，支持 {@code \P{...}} 取反）、
 * 量词 {@code *} {@code +} {@code ?} {@code {n}} {@code {n,m}} {@code {n,}}（含懒惰后缀）、
 * 分组与交替 {@code (a|b)}、非捕获组 {@code (?:...)}；锚 {@code ^}/{@code $} 忽略。</p>
 * <p>不支持的结构（反向引用、环视、命名组、未知 Unicode 属性、非法量词、重复上限超阈值）
 * 抛出 {@link RegexGenerationException} 打断生成。通过 {@link #of(String, RandomGenerator)} 注入熵源，
 * 同一种子生成结果可复现。</p>
 *
 * <pre>{@code
 * String value = RegexStringGenerator.of("[a-z]{2,4}").generate();
 * }</pre>
 */
@ThreadFragile("持有注入的共享 RandomGenerator，其线程安全性取决于实现，多线程并发 generate() 需自行同步")
public final class RegexStringGenerator {

    /** 单次重复数量的上限，超出视为不支持。 */
    private static final int MAX_REPEAT = 256;

    private final RandomGenerator random;
    private final String pattern;

    private RegexStringGenerator(String pattern, RandomGenerator random) {
        this.pattern = pattern;
        this.random = random;
    }

    /** 用默认随机源构造生成器。 */
    public static RegexStringGenerator of(String pattern) {
        return new RegexStringGenerator(pattern, new Random());
    }

    /** 注入熵源构造生成器（同一种子可复现）。 */
    public static RegexStringGenerator of(String pattern, RandomGenerator entropy) {
        return new RegexStringGenerator(pattern, entropy);
    }

    /** @return 匹配 pattern 的随机字符串；遇到不支持的结构抛 {@link RegexGenerationException} */
    public String generate() {
        try {
            Parser p = new Parser(pattern);
            List<Atom> atoms = p.parseSequence();
            if (!p.isAtEnd()) {
                throw new RegexGenerationException("未消费完的正则: " + pattern);
            }
            StringBuilder sb = new StringBuilder();
            for (Atom atom : atoms) {
                atom.append(sb);
            }
            return sb.toString();
        } catch (RegexGenerationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RegexGenerationException("不支持的正则语法: " + pattern, e);
        }
    }

    // ── 原子模型 ──

    private interface Atom {
        void append(StringBuilder sb);
    }

    private abstract class BaseAtom implements Atom {
        private int min = 1; // 无量词时恰好生成一次
        private int max = 1;

        void quantifier(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public void append(StringBuilder sb) {
            int count = count();
            for (int i = 0; i < count; i++) {
                emit(sb);
            }
        }

        private int count() {
            if (max < 0) {
                return min + randomOf(0, Math.min(3, MAX_REPEAT - min)); // {n,} 上限放宽
            }
            if (max == min) {
                return min;
            }
            return min + randomOf(0, max - min);
        }

        abstract void emit(StringBuilder sb);

        private int randomOf(int lo, int hi) {
            if (hi <= lo) {
                return lo;
            }
            return lo + random.nextInt(hi - lo + 1);
        }
    }

    private final class LiteralAtom extends BaseAtom {
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
            sb.append(chars.get(random.nextInt(chars.size())));
        }
    }

    /** Unicode 属性原子：随机码点 + 拒绝采样，取反时用 {@code \P{...}} 语义。 */
    private final class UnicodeClassAtom extends BaseAtom {
        private final int property;
        private final boolean negate;

        UnicodeClassAtom(int property, boolean negate) {
            this.property = property;
            this.negate = negate;
        }

        @Override
        void emit(StringBuilder sb) {
            for (int attempt = 0; attempt < 10000; attempt++) {
                int cp = randomCodePoint();
                if (matchesProperty(cp, property) != negate) {
                    sb.appendCodePoint(cp);
                    return;
                }
            }
            throw new IllegalArgumentException("无法生成属性字符");
        }
    }

    private final class GroupAtom extends BaseAtom {
        private final List<List<Atom>> alternatives;

        GroupAtom(List<List<Atom>> alternatives) {
            this.alternatives = alternatives;
        }

        @Override
        void emit(StringBuilder sb) {
            List<Atom> branch = alternatives.get(random.nextInt(alternatives.size()));
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
                case 's' -> new CharClassAtom(whitespace());
                case 'D' -> new CharClassAtom(complement(digits()));
                case 'W' -> new CharClassAtom(complement(alnum()));
                case 'S' -> new CharClassAtom(complement(whitespace()));
                case 't' -> new LiteralAtom('\t');
                case 'n' -> new LiteralAtom('\n');
                case 'r' -> new LiteralAtom('\r');
                case 'f' -> new LiteralAtom('\f');
                case '0' -> new LiteralAtom('\0');
                case 'p', 'P' -> parseUnicodeProperty(c == 'P');
                case '1', '2', '3', '4', '5', '6', '7', '8', '9' ->
                        throw new IllegalArgumentException("反向引用不支持");
                default -> new LiteralAtom(c);
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
            int property = propertyOf(name);
            if (property < 0) {
                throw new IllegalArgumentException("未知属性: " + name);
            }
            pos = close + 1;
            return new UnicodeClassAtom(property, negate);
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
                List<Character> diff = new ArrayList<>(complement(new ArrayList<>(chars)));
                chars = diff;
            }
            if (chars.isEmpty()) {
                throw new IllegalArgumentException("空字符类");
            }
            return new CharClassAtom(chars);
        }

        /** 字符类内的转义：\d \w \s \D \W \S、\t \n \r \f \0，其余作字面量。 */
        private void addEscape(List<Character> chars) {
            if (isAtEnd()) {
                throw new IllegalArgumentException("转义不完整");
            }
            char c = pattern.charAt(pos);
            pos++;
            switch (c) {
                case 'd' -> chars.addAll(digits());
                case 'w' -> chars.addAll(alnum());
                case 's' -> chars.addAll(whitespace());
                case 'D' -> chars.addAll(complement(digits()));
                case 'W' -> chars.addAll(complement(alnum()));
                case 'S' -> chars.addAll(complement(whitespace()));
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
            } else {
                throw new IllegalArgumentException("分组未闭合");
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
            if (atom.min < 0 || (atom.max >= 0 && atom.max > MAX_REPEAT)
                    || (atom.max < 0 && atom.min > MAX_REPEAT)) {
                throw new IllegalArgumentException("重复上限超阈值");
            }
            // 懒惰量词后缀 ? 忽略
            if (!isAtEnd() && pattern.charAt(pos) == '?') {
                pos++;
            }
        }
    }

    // ── Unicode 属性 ──

    private static final int PROP_LETTER = 0;
    private static final int PROP_LOWER = 1;
    private static final int PROP_UPPER = 2;
    private static final int PROP_NUMBER = 3;
    private static final int PROP_DIGIT = 4;
    private static final int PROP_PUNCT = 5;
    private static final int PROP_SEPARATOR = 6;

    private static int propertyOf(String name) {
        return switch (name) {
            case "L" -> PROP_LETTER;
            case "Ll" -> PROP_LOWER;
            case "Lu" -> PROP_UPPER;
            case "N" -> PROP_NUMBER;
            case "Nd" -> PROP_DIGIT;
            case "P" -> PROP_PUNCT;
            case "Z" -> PROP_SEPARATOR;
            default -> -1;
        };
    }

    private static boolean matchesProperty(int cp, int property) {
        int type = Character.getType(cp);
        return switch (property) {
            case PROP_LETTER -> type == Character.UPPERCASE_LETTER
                    || type == Character.LOWERCASE_LETTER
                    || type == Character.TITLECASE_LETTER
                    || type == Character.MODIFIER_LETTER
                    || type == Character.OTHER_LETTER;
            case PROP_LOWER -> type == Character.LOWERCASE_LETTER;
            case PROP_UPPER -> type == Character.UPPERCASE_LETTER;
            case PROP_NUMBER -> type == Character.LETTER_NUMBER
                    || type == Character.OTHER_NUMBER
                    || type == Character.DECIMAL_DIGIT_NUMBER;
            case PROP_DIGIT -> type == Character.DECIMAL_DIGIT_NUMBER;
            case PROP_PUNCT -> isPunctuation(cp);
            case PROP_SEPARATOR -> isSeparator(cp);
            default -> false;
        };
    }

    private static boolean isPunctuation(int cp) {
        return switch (Character.getType(cp)) {
            case Character.CONNECTOR_PUNCTUATION,
                    Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }

    private static boolean isSeparator(int cp) {
        return switch (Character.getType(cp)) {
            case Character.SPACE_SEPARATOR,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR -> true;
            default -> false;
        };
    }

    private int randomCodePoint() {
        // 限定 BMP：避免补充平面字符在拒绝采样与 Pattern 匹配间的不一致
        return random.nextInt(0x10000);
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
        l.add('_');
        return l;
    }

    private static List<Character> whitespace() {
        return List.of(' ', '\t', '\n', '\r', '\f');
    }

    /** 可打印 ASCII：'!'..'~'。 */
    private static List<Character> allChars() {
        List<Character> l = new ArrayList<>();
        for (char c = '!'; c <= '~'; c++) {
            l.add(c);
        }
        return l;
    }

    /** 从可打印 ASCII 中排除给定集合（取反语义）。 */
    private static List<Character> complement(List<Character> excluded) {
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
