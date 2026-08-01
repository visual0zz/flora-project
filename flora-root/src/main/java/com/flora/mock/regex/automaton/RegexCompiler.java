package com.flora.mock.regex.automaton;

import java.util.ArrayList;
import java.util.List;

/**
 * 正则编译器：正则文本 → AST → NFA（Thompson 构造）。
 * <p>支持扩展语法：字面量、{@code .}、字符类（范围/取反/内嵌简写/POSIX/并集差集）、
 * 简写 {@code \d \w \s \D \W \S}、转义 {@code \t \n \r \f \0}、十六进制/Unicode 转义、
 * Unicode 属性 {@code \p{...}}/{@code \P{...}}、量词 {@code * + ? {n} {n,m} {n,}}（懒惰后缀忽略）、
 * 分组与交替、非捕获组 {@code (?:...)}。锚 {@code ^}/{@code $} 忽略。</p>
 * <p>不支持（抛 {@link AutomatonException}）：反向引用、环视、命名组、未知属性、
 * 非法量词、字符类/分组/量词未闭合。</p>
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

    // ── 解析阶段 ──

    private RegexNode parseTopLevel() {
        while (pos < pattern.length() && pattern.charAt(pos) == '^') {
            pos++;
        }
        List<RegexNode> branches = new ArrayList<>();
        while (true) {
            List<RegexNode> seq = parseSequence();
            branches.add(seq.size() == 1 ? seq.get(0) : new ConcatNode(seq));
            if (pos < pattern.length() && pattern.charAt(pos) == '|') {
                pos++;
            } else {
                break;
            }
        }
        while (pos < pattern.length() && pattern.charAt(pos) == '$') {
            pos++;
        }
        if (pos != pattern.length()) {
            throw new AutomatonException("未消费完的正则: " + pattern);
        }
        if (branches.size() == 1) {
            return branches.get(0);
        }
        return new AlternationNode(branches);
    }

    private List<RegexNode> parseSequence() {
        List<RegexNode> nodes = new ArrayList<>();
        while (pos < pattern.length()) {
            char c = pattern.charAt(pos);
            if (c == '|' || c == ')') {
                break;
            }
            if (c == '^' || c == '$') {
                pos++;
                continue;
            }
            nodes.add(parseTerm());
        }
        return nodes;
    }

    private RegexNode parseTerm() {
        char c = pattern.charAt(pos);
        RegexNode body;
        if (c == '.') {
            pos++;
            body = new CharNode(dotChars());
        } else if (c == '\\') {
            pos++;
            body = new CharNode(parseEscapeCharSet());
        } else if (c == '[') {
            pos++;
            body = new CharNode(parseCharClass());
        } else if (c == '(') {
            pos++;
            body = parseGroup();
        } else {
            pos++;
            body = new CharNode(CharSet.ofChar(c));
        }
        return parseQuantifier(body);
    }

    private RegexNode parseGroup() {
        if (pos < pattern.length() && pattern.charAt(pos) == '?') {
            if (pos + 1 < pattern.length() && pattern.charAt(pos + 1) == ':') {
                pos += 2; // (?: 非捕获组
            } else {
                throw new AutomatonException("不支持的分组前缀（环视/命名组）: " + pattern);
            }
        }
        List<RegexNode> branches = new ArrayList<>();
        while (true) {
            List<RegexNode> branch = parseSequence();
            branches.add(branch.size() == 1 ? branch.get(0) : new ConcatNode(branch));
            if (pos < pattern.length() && pattern.charAt(pos) == '|') {
                pos++;
            } else {
                break;
            }
        }
        if (pos < pattern.length() && pattern.charAt(pos) == ')') {
            pos++;
        } else {
            throw new AutomatonException("分组未闭合: " + pattern);
        }
        if (branches.size() == 1) {
            return branches.get(0);
        }
        return new AlternationNode(branches);
    }

    private RegexNode parseQuantifier(RegexNode body) {
        if (pos >= pattern.length()) {
            return body;
        }
        char c = pattern.charAt(pos);
        int min, max;
        switch (c) {
            case '*' -> {
                pos++;
                min = 0;
                max = -1;
            }
            case '+' -> {
                pos++;
                min = 1;
                max = -1;
            }
            case '?' -> {
                pos++;
                min = 0;
                max = 1;
            }
            case '{' -> {
                int close = pattern.indexOf('}', pos);
                if (close < 0) {
                    throw new AutomatonException("量词未闭合: " + pattern);
                }
                String body2 = pattern.substring(pos + 1, close);
                String[] parts = body2.split(",", -1);
                try {
                    min = parts[0].isEmpty() ? 0 : Integer.parseInt(parts[0]);
                    if (parts.length == 1) {
                        max = min;
                    } else {
                        max = parts[1].isEmpty() ? -1 : Integer.parseInt(parts[1]);
                    }
                } catch (NumberFormatException e) {
                    throw new AutomatonException("非法量词: " + body2);
                }
                if (min < 0 || (max >= 0 && max < min)) {
                    throw new AutomatonException("非法量词: " + body2);
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
        if (pos < pattern.length() && pattern.charAt(pos) == '?') {
            pos++;
        }
        return new RepeatNode(body, min, max);
    }

    // ── 编译阶段（AST → NFA）──

    private Nfa toNfa(RegexNode root) {
        Nfa nfa = new Nfa();
        Fragment frag = compile(nfa, root);
        nfa.setStart(frag.start);
        nfa.addAccept(frag.end);
        return nfa;
    }

    private Fragment compile(Nfa nfa, RegexNode node) {
        return switch (node) {
            case CharNode cn -> {
                if (cn.charSet().isEmpty()) {
                    // 空字符集：不可达，start/end 无连接（语言为空）
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
                    nfa.addEpsilon(cursor, f.start);
                    cursor = f.end;
                }
                yield new Fragment(s, cursor);
            }
            case AlternationNode an -> {
                int s = nfa.newState();
                int e = nfa.newState();
                for (RegexNode branch : an.branches()) {
                    Fragment f = compile(nfa, branch);
                    nfa.addEpsilon(s, f.start);
                    nfa.addEpsilon(f.end, e);
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
            nfa.addEpsilon(cursor, f.start);
            cursor = f.end;
        }
        if (max < 0) {
            // 无界：尾部一份 body + 回环
            Fragment f = compile(nfa, rn.body());
            nfa.addEpsilon(cursor, f.start);
            nfa.addEpsilon(f.end, cursor); // 回环
            nfa.addEpsilon(f.end, nfa.newState()); // 出口（下面统一处理）
            // 修正：用独立出口
            int exit = nfa.newState();
            nfa.addEpsilon(f.end, exit);
            return new Fragment(start, exit);
        }
        // 有界：再补 max-min 份可选
        for (int i = min; i < max; i++) {
            Fragment f = compile(nfa, rn.body());
            nfa.addEpsilon(cursor, f.start);
            int exit = nfa.newState();
            nfa.addEpsilon(f.end, exit);
            nfa.addEpsilon(cursor, exit); // 可选跳过
            cursor = exit;
        }
        return new Fragment(start, cursor);
    }

    private record Fragment(int start, int end) {
    }

    // ── 字符解析 ──

    private CharSet parseEscapeCharSet() {
        if (pos >= pattern.length()) {
            throw new AutomatonException("转义不完整: " + pattern);
        }
        char c = pattern.charAt(pos);
        pos++;
        return switch (c) {
            case 'd' -> CharSets.digit();
            case 'w' -> CharSets.word();
            case 's' -> CharSets.whitespace();
            case 'D' -> CharSet.complement(CharSets.digit());
            case 'W' -> CharSet.complement(CharSets.word());
            case 'S' -> CharSet.complement(CharSets.whitespace());
            case 't' -> CharSet.ofChar('\t');
            case 'n' -> CharSet.ofChar('\n');
            case 'r' -> CharSet.ofChar('\r');
            case 'f' -> CharSet.ofChar('\f');
            case '0' -> CharSet.ofChar('\0');
            case 'x' -> CharSet.ofChar(parseHex());
            case 'u' -> CharSet.ofChar(parseUnicodeEscape());
            case 'p', 'P' -> parseUnicodeProperty(c == 'P');
            case '1', '2', '3', '4', '5', '6', '7', '8', '9' ->
                    throw new AutomatonException("反向引用不支持: \\" + c);
            default -> CharSet.ofChar(c);
        };
    }

    /** \x{..} 或 \xNN。 */
    private int parseHex() {
        if (pos < pattern.length() && pattern.charAt(pos) == '{') {
            int close = pattern.indexOf('}', pos);
            if (close < 0) {
                throw new AutomatonException("十六进制转义未闭合");
            }
            String hex = pattern.substring(pos + 1, close);
            pos = close + 1;
            try {
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw new AutomatonException("非法十六进制: " + hex);
            }
        }
        if (pos + 2 > pattern.length()) {
            throw new AutomatonException("十六进制转义不完整");
        }
        String hex = pattern.substring(pos, pos + 2);
        pos += 2;
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
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new AutomatonException("非法 Unicode 转义: " + hex);
        }
    }

    private CharSet parseUnicodeProperty(boolean negate) {
        if (pos >= pattern.length() || pattern.charAt(pos) != '{') {
            throw new AutomatonException("缺少 {");
        }
        int close = pattern.indexOf('}', pos);
        if (close < 0) {
            throw new AutomatonException("缺少 }");
        }
        String name = pattern.substring(pos + 1, close);
        pos = close + 1;
        CharSet set = UnicodeProperties.charSetOf(name);
        if (set == null) {
            throw new AutomatonException("未知属性: " + name);
        }
        return negate ? CharSet.complement(set) : set;
    }

    /** 字符类 [..]：范围/取反/内嵌简写/POSIX/并集差集。 */
    private CharSet parseCharClass() {
        boolean negate = false;
        if (pos < pattern.length() && pattern.charAt(pos) == '^') {
            negate = true;
            pos++;
        }
        CharSet main = parseClassItems();
        // [A&&B] = A∩B（B 可为普通项或嵌套字符类）
        while (pos + 1 < pattern.length() && pattern.charAt(pos) == '&'
                && pattern.charAt(pos + 1) == '&') {
            pos += 2;
            CharSet second;
            if (pos < pattern.length() && pattern.charAt(pos) == '[') {
                pos++;
                second = parseCharClass();
            } else {
                second = parseClassItems();
            }
            main = CharSet.intersect(main, second);
        }
        if (pos >= pattern.length() || pattern.charAt(pos) != ']') {
            throw new AutomatonException("字符类未闭合: " + pattern);
        }
        pos++;
        // 空交集是合法的空语言，允许（由自动机 isSatisfiable 表达）
        return negate ? CharSet.complement(main) : main;
    }

    private CharSet parseClassItems() {
        CharSet result = CharSet.EMPTY;
        while (pos < pattern.length() && pattern.charAt(pos) != ']'
                && !(pattern.charAt(pos) == '&' && pos + 1 < pattern.length()
                && pattern.charAt(pos + 1) == '&')) {
            char c = pattern.charAt(pos);
            CharSet item;
            if (c == '\\') {
                pos++;
                item = parseClassEscape();
            } else if (c == '[') {
                // 嵌套字符类：[a-z[0-9]] 语义（Java 中不是 POSIX，是嵌套并集）
                pos++;
                item = parseCharClass();
            } else {
                pos++;
                item = CharSet.ofChar(c);
                // 范围 x-y
                if (pos < pattern.length() && pattern.charAt(pos) == '-'
                        && pos + 1 < pattern.length() && pattern.charAt(pos + 1) != ']'
                        && pattern.charAt(pos + 1) != '&') {
                    pos++;
                    char end = pattern.charAt(pos);
                    pos++;
                    item = CharSet.ofRange(c, end);
                }
            }
            result = CharSet.union(result, item);
        }
        return result;
    }

    private CharSet parseClassEscape() {
        if (pos >= pattern.length()) {
            throw new AutomatonException("转义不完整");
        }
        char c = pattern.charAt(pos);
        pos++;
        return switch (c) {
            case 'd' -> CharSets.digit();
            case 'w' -> CharSets.word();
            case 's' -> CharSets.whitespace();
            case 'D' -> CharSet.complement(CharSets.digit());
            case 'W' -> CharSet.complement(CharSets.word());
            case 'S' -> CharSet.complement(CharSets.whitespace());
            case 't' -> CharSet.ofChar('\t');
            case 'n' -> CharSet.ofChar('\n');
            case 'r' -> CharSet.ofChar('\r');
            case 'f' -> CharSet.ofChar('\f');
            case '0' -> CharSet.ofChar('\0');
            case 'x' -> CharSet.ofChar(parseHex());
            case 'u' -> CharSet.ofChar(parseUnicodeEscape());
            case 'p', 'P' -> parseUnicodeProperty(c == 'P');
            default -> CharSet.ofChar(c);
        };
    }

    private static CharSet dotChars() {
        // 与 Java Pattern 一致：除行终止符外任意字符
        return CharSet.complement(CharSet.ofRange('\n', '\r'));
    }

    // ── 字符集定义 ──

    /** Unicode 属性 → 字符集（复用 mock.regex 的区间池）。 */
    private static final class UnicodeProperties {
        static CharSet charSetOf(String name) {
            int[] ranges = com.flora.mock.regex.impl.UnicodePropertyRanges.rangesOf(name);
            return ranges == null ? null : CharSet.of(ranges);
        }
    }

    private static final class CharSets {
        static CharSet digit() {
            return CharSet.ofRange('0', '9');
        }

        static CharSet word() {
            return CharSet.of(new int[]{'0', '9', 'A', 'Z', '_', '_', 'a', 'z'});
        }

        static CharSet whitespace() {
            return CharSet.of(new int[]{'\t', '\t', '\n', '\n', '\f', '\f', '\r', '\r', ' ', ' '});
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
