package com.flora.root.mock.regex.automaton;

import com.flora.root.mock.regex.impl.UnicodePropertyRanges;

import java.util.ArrayList;
import java.util.List;

/**
 * 正则编译器：正则文本 → AST → NFA（Thompson 构造）。
 * <p>按码点解析（增补平面字符视为单个字符），字符集合覆盖全部 Unicode 码点。
 * 支持语法：字面量、{@code .}、字符类（范围/取反/内嵌简写/嵌套并集/交集）、
 * 简写 {@code \d \w \s \D \W \S}、转义 {@code \t \n \r \f \0 \a \e}、十六进制/Unicode 转义、
 * Unicode 属性 {@code \p{...}}/{@code \P{...}}、量词 {@code * + ? {n} {n,m} {n,}}（懒惰后缀忽略）、
 * 分组与交替、非捕获组 {@code (?:...)}。锚 {@code ^}/{@code $} 忽略。</p>
 * <p>不支持（抛 {@link AutomatonException}，不做静默降级）：反向引用、环视、命名组、内联标志、
 * 未知属性、未知转义、悬空/非法量词、字符类/分组/量词未闭合。</p>
 */
final class RegexCompiler {

    /** 单次重复数量的上限，超出视为不支持（防巨大 NFA）。 */
    private static final int MAX_REPEAT = 256;

    private final String pattern;
    private int pos;

    private RegexCompiler(String pattern) {
        this.pattern = pattern;
    }

    /** 编译正则文本为 NFA。 */
    static Nfa compile(String pattern) {
        RegexCompiler c = new RegexCompiler(pattern);
        RegexNode root = c.parseTopLevel();
        return c.toNfa(root);
    }

    // ── 扫描 ──

    private boolean atEnd() {
        return pos >= pattern.length();
    }

    private int peek() {
        return pattern.codePointAt(pos);
    }

    /** 读取下一个码点并前进（按字符宽度推进，代理对整体消费）。 */
    private int next() {
        int cp = pattern.codePointAt(pos);
        pos += Character.charCount(cp);
        return cp;
    }

    // ── 解析阶段 ──

    private RegexNode parseTopLevel() {
        while (!atEnd() && peek() == '^') {
            next();
        }
        List<RegexNode> branches = new ArrayList<>();
        while (true) {
            List<RegexNode> seq = parseSequence();
            branches.add(seq.size() == 1 ? seq.get(0) : new ConcatNode(seq));
            if (!atEnd() && peek() == '|') {
                next();
            } else {
                break;
            }
        }
        while (!atEnd() && peek() == '$') {
            next();
        }
        if (pos != pattern.length()) {
            throw new AutomatonException("未消费完的正则: " + pattern);
        }
        return branches.size() == 1 ? branches.get(0) : new AlternationNode(branches);
    }

    private List<RegexNode> parseSequence() {
        List<RegexNode> nodes = new ArrayList<>();
        while (!atEnd()) {
            int cp = peek();
            if (cp == '|' || cp == ')') {
                break;
            }
            if (cp == '^' || cp == '$') {
                next();
                continue;
            }
            nodes.add(parseTerm());
        }
        return nodes;
    }

    private RegexNode parseTerm() {
        int cp = next();
        RegexNode body = switch (cp) {
            case '.' -> new CharNode(dotChars());
            case '\\' -> new CharNode(parseEscapeCharSet());
            case '[' -> new CharNode(parseCharClass());
            case '(' -> parseGroup();
            case '*', '+', '?', '{' -> throw new AutomatonException("悬空量词: " + pattern);
            default -> new CharNode(CharSet.ofCodePoint(cp));
        };
        return parseQuantifier(body);
    }

    private RegexNode parseGroup() {
        if (!atEnd() && peek() == '?') {
            if (pos + 1 < pattern.length() && pattern.charAt(pos + 1) == ':') {
                pos += 2; // (?: 非捕获组
            } else {
                throw new AutomatonException("不支持的分组前缀（内联标志/环视/命名组）: " + pattern);
            }
        }
        List<RegexNode> branches = new ArrayList<>();
        while (true) {
            List<RegexNode> branch = parseSequence();
            branches.add(branch.size() == 1 ? branch.get(0) : new ConcatNode(branch));
            if (!atEnd() && peek() == '|') {
                next();
            } else {
                break;
            }
        }
        if (atEnd() || peek() != ')') {
            throw new AutomatonException("分组未闭合: " + pattern);
        }
        next();
        return branches.size() == 1 ? branches.get(0) : new AlternationNode(branches);
    }

    private RegexNode parseQuantifier(RegexNode body) {
        if (atEnd()) {
            return body;
        }
        int min;
        int max;
        switch (peek()) {
            case '*' -> {
                next();
                min = 0;
                max = -1;
            }
            case '+' -> {
                next();
                min = 1;
                max = -1;
            }
            case '?' -> {
                next();
                min = 0;
                max = 1;
            }
            case '{' -> {
                int close = pattern.indexOf('}', pos);
                if (close < 0) {
                    throw new AutomatonException("量词未闭合: " + pattern);
                }
                String spec = pattern.substring(pos + 1, close);
                String[] parts = spec.split(",", -1);
                try {
                    min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
                    if (parts.length == 1) {
                        max = min;
                    } else {
                        max = parts[1].isEmpty() ? -1 : Integer.parseInt(parts[1]);
                    }
                } catch (NumberFormatException e) {
                    throw new AutomatonException("非法量词: " + spec);
                }
                if (min < 0 || (max >= 0 && max < min)) {
                    throw new AutomatonException("非法量词: " + spec);
                }
                pos = close + 1;
            }
            default -> {
                return body;
            }
        }
        // 重复上限阈值：防止超大量词产生巨大 NFA
        if (min > MAX_REPEAT || (max >= 0 && max > MAX_REPEAT)) {
            throw new AutomatonException("重复上限超阈值: " + min + (max >= 0 ? "," + max : ",}"));
        }
        // 懒惰后缀 ? 忽略
        if (!atEnd() && peek() == '?') {
            next();
        }
        return new RepeatNode(body, min, max);
    }

    // ── 编译阶段（AST → NFA）──

    private Nfa toNfa(RegexNode root) {
        Nfa nfa = new Nfa();
        Fragment frag = compile(nfa, root);
        nfa.setStart(frag.start());
        nfa.addAccept(frag.end());
        return nfa;
    }

    private Fragment compile(Nfa nfa, RegexNode node) {
        return switch (node) {
            case CharNode cn -> {
                if (cn.charSet().isEmpty()) {
                    // 空字符集：语言为空，start/end 不连通
                    yield new Fragment(nfa.newState(), nfa.newState());
                }
                int s = nfa.newState();
                int e = nfa.newState();
                nfa.addTransition(s, cn.charSet(), e);
                yield new Fragment(s, e);
            }
            case ConcatNode cc -> {
                int s = nfa.newState();
                int cursor = s;
                for (RegexNode child : cc.children()) {
                    Fragment f = compile(nfa, child);
                    nfa.addEpsilon(cursor, f.start());
                    cursor = f.end();
                }
                yield new Fragment(s, cursor);
            }
            case AlternationNode an -> {
                int s = nfa.newState();
                int e = nfa.newState();
                for (RegexNode branch : an.branches()) {
                    Fragment f = compile(nfa, branch);
                    nfa.addEpsilon(s, f.start());
                    nfa.addEpsilon(f.end(), e);
                }
                yield new Fragment(s, e);
            }
            case RepeatNode rn -> compileRepeat(nfa, rn);
        };
    }

    private Fragment compileRepeat(Nfa nfa, RepeatNode rn) {
        int min = rn.min();
        int max = rn.max();
        int start = nfa.newState();
        int cursor = start;
        // min 次必选
        for (int i = 0; i < min; i++) {
            Fragment f = compile(nfa, rn.body());
            nfa.addEpsilon(cursor, f.start());
            cursor = f.end();
        }
        if (max < 0) {
            // 无界：一份 body + 回环，出口独立
            Fragment f = compile(nfa, rn.body());
            int exit = nfa.newState();
            nfa.addEpsilon(cursor, f.start());
            nfa.addEpsilon(f.end(), cursor); // 回环
            nfa.addEpsilon(f.end(), exit);
            nfa.addEpsilon(cursor, exit);    // 零次
            return new Fragment(start, exit);
        }
        // 有界：再补 max-min 份可选
        for (int i = min; i < max; i++) {
            Fragment f = compile(nfa, rn.body());
            int exit = nfa.newState();
            nfa.addEpsilon(cursor, f.start());
            nfa.addEpsilon(f.end(), exit);
            nfa.addEpsilon(cursor, exit); // 可选跳过
            cursor = exit;
        }
        return new Fragment(start, cursor);
    }

    private record Fragment(int start, int end) {
    }

    // ── 字符解析 ──

    private CharSet parseEscapeCharSet() {
        if (atEnd()) {
            throw new AutomatonException("转义不完整: " + pattern);
        }
        int c = next();
        return switch (c) {
            case 'd' -> CharSets.digit();
            case 'w' -> CharSets.word();
            case 's' -> CharSets.whitespace();
            case 'D' -> CharSet.complement(CharSets.digit());
            case 'W' -> CharSet.complement(CharSets.word());
            case 'S' -> CharSet.complement(CharSets.whitespace());
            case 't' -> CharSet.ofCodePoint('\t');
            case 'n' -> CharSet.ofCodePoint('\n');
            case 'r' -> CharSet.ofCodePoint('\r');
            case 'f' -> CharSet.ofCodePoint('\f');
            case 'a' -> CharSet.ofCodePoint(0x07);
            case 'e' -> CharSet.ofCodePoint(0x1B);
            case '0' -> CharSet.ofCodePoint('\0');
            case 'x' -> CharSet.ofCodePoint(parseHex());
            case 'u' -> CharSet.ofCodePoint(parseUnicodeEscape());
            case 'p', 'P' -> parseUnicodeProperty(c == 'P');
            case '1', '2', '3', '4', '5', '6', '7', '8', '9' ->
                    throw new AutomatonException("反向引用不支持: \\" + (char) c);
            case 'b', 'B', 'A', 'z', 'Z', 'G', 'Q', 'E', 'k', 'R', 'X', 'h', 'H', 'v', 'V' ->
                    throw new AutomatonException("不支持的转义: \\" + (char) c);
            default -> CharSet.ofCodePoint(c);
        };
    }

    /** \x{..} 或 \xNN。 */
    private int parseHex() {
        if (!atEnd() && peek() == '{') {
            int close = pattern.indexOf('}', pos);
            if (close < 0) {
                throw new AutomatonException("十六进制转义未闭合");
            }
            String hex = pattern.substring(pos + 1, close);
            pos = close + 1;
            return parseHexDigits(hex);
        }
        if (pos + 2 > pattern.length()) {
            throw new AutomatonException("十六进制转义不完整");
        }
        String hex = pattern.substring(pos, pos + 2);
        pos += 2;
        return parseHexDigits(hex);
    }

    private static int parseHexDigits(String hex) {
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new AutomatonException("非法十六进制: " + hex);
        }
    }

    /** Unicode 转义（反斜杠 u 后跟 4 位十六进制）。 */
    private int parseUnicodeEscape() {
        if (pos + 4 > pattern.length()) {
            throw new AutomatonException("Unicode 转义不完整");
        }
        String hex = pattern.substring(pos, pos + 4);
        pos += 4;
        return parseHexDigits(hex);
    }

    private CharSet parseUnicodeProperty(boolean negate) {
        if (atEnd() || peek() != '{') {
            throw new AutomatonException("缺少 {");
        }
        int close = pattern.indexOf('}', pos);
        if (close < 0) {
            throw new AutomatonException("缺少 }");
        }
        String name = pattern.substring(pos + 1, close);
        pos = close + 1;
        int[] ranges = UnicodePropertyRanges.rangesOf(name);
        if (ranges == null) {
            throw new AutomatonException("未知属性: " + name);
        }
        CharSet set = CharSet.of(ranges);
        return negate ? CharSet.complement(set) : set;
    }

    /** 字符类 [..]：范围/取反/内嵌简写/嵌套并集/交集。 */
    private CharSet parseCharClass() {
        boolean negate = false;
        if (!atEnd() && peek() == '^') {
            negate = true;
            next();
        }
        CharSet main = parseClassItems();
        // [A&&B] = A∩B（B 可为普通项或嵌套字符类）
        while (pos + 1 < pattern.length() && pattern.charAt(pos) == '&'
                && pattern.charAt(pos + 1) == '&') {
            pos += 2;
            CharSet second;
            if (!atEnd() && peek() == '[') {
                next();
                second = parseCharClass();
            } else {
                second = parseClassItems();
            }
            main = CharSet.intersect(main, second);
        }
        if (atEnd() || peek() != ']') {
            throw new AutomatonException("字符类未闭合: " + pattern);
        }
        next();
        // 空交集是合法的空语言，允许（由自动机 isSatisfiable 表达）
        return negate ? CharSet.complement(main) : main;
    }

    private CharSet parseClassItems() {
        CharSet result = CharSet.EMPTY;
        while (!atEnd() && peek() != ']'
                && !(peek() == '&' && pos + 1 < pattern.length() && pattern.charAt(pos + 1) == '&')) {
            int c = peek();
            CharSet item;
            if (c == '\\') {
                next();
                item = parseClassEscape();
                result = CharSet.union(result, item);
                continue;
            }
            if (c == '[') {
                // 嵌套字符类：[a-z[0-9]] 是并集语义
                next();
                item = parseCharClass();
                result = CharSet.union(result, item);
                continue;
            }
            next();
            // 范围 x-y
            if (!atEnd() && peek() == '-' && pos + 1 < pattern.length()
                    && pattern.charAt(pos + 1) != ']' && pattern.charAt(pos + 1) != '&') {
                next();
                int end = next();
                if (end < c) {
                    throw new AutomatonException("非法的字符范围: " + pattern);
                }
                item = CharSet.ofRange(c, end);
            } else {
                item = CharSet.ofCodePoint(c);
            }
            result = CharSet.union(result, item);
        }
        return result;
    }

    private CharSet parseClassEscape() {
        if (atEnd()) {
            throw new AutomatonException("转义不完整");
        }
        int c = next();
        return switch (c) {
            case 'd' -> CharSets.digit();
            case 'w' -> CharSets.word();
            case 's' -> CharSets.whitespace();
            case 'D' -> CharSet.complement(CharSets.digit());
            case 'W' -> CharSet.complement(CharSets.word());
            case 'S' -> CharSet.complement(CharSets.whitespace());
            case 't' -> CharSet.ofCodePoint('\t');
            case 'n' -> CharSet.ofCodePoint('\n');
            case 'r' -> CharSet.ofCodePoint('\r');
            case 'f' -> CharSet.ofCodePoint('\f');
            case 'b' -> CharSet.ofCodePoint(0x08); // 字符类内 \b 是退格
            case 'a' -> CharSet.ofCodePoint(0x07);
            case 'e' -> CharSet.ofCodePoint(0x1B);
            case '0' -> CharSet.ofCodePoint('\0');
            case 'x' -> CharSet.ofCodePoint(parseHex());
            case 'u' -> CharSet.ofCodePoint(parseUnicodeEscape());
            case 'p', 'P' -> parseUnicodeProperty(c == 'P');
            default -> CharSet.ofCodePoint(c);
        };
    }

    /**
     * {@code .} 的语义：除行终止符外的任意字符（与 JDK 一致）。
     * 行终止符为 {@code \n}、{@code \r}、{@code \u0085}、{@code \u2028}、{@code \u2029}。
     */
    private static CharSet dotChars() {
        return CharSet.complement(CharSet.of(new int[]{
                '\n', '\n', '\r', '\r', 0x85, 0x85, 0x2028, 0x2028, 0x2029, 0x2029}));
    }

    // ── 字符集定义 ──

    private static final class CharSets {
        static CharSet digit() {
            return CharSet.ofRange('0', '9');
        }

        static CharSet word() {
            return CharSet.of(new int[]{'0', '9', 'A', 'Z', '_', '_', 'a', 'z'});
        }

        /** {@code \s}：与 JDK 一致，含垂直制表符 0x0B。 */
        static CharSet whitespace() {
            return CharSet.of(new int[]{0x09, 0x0D, ' ', ' '});
        }
    }

    // ── AST ──

    private sealed interface RegexNode permits CharNode, ConcatNode, AlternationNode, RepeatNode {
    }

    private record CharNode(CharSet charSet) implements RegexNode {
    }

    private record ConcatNode(List<RegexNode> children) implements RegexNode {
    }

    private record AlternationNode(List<RegexNode> branches) implements RegexNode {
    }

    private record RepeatNode(RegexNode body, int min, int max) implements RegexNode {
    }
}
