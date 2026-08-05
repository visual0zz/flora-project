package com.flora.syntax.peg.impl;

import com.flora.syntax.peg.GrammarException;
import com.flora.syntax.peg.impl.RuleDefs.Alt;
import com.flora.syntax.peg.impl.RuleDefs.EAnd;
import com.flora.syntax.peg.impl.RuleDefs.EAny;
import com.flora.syntax.peg.impl.RuleDefs.EClass;
import com.flora.syntax.peg.impl.RuleDefs.Elem;
import com.flora.syntax.peg.impl.RuleDefs.EGroup;
import com.flora.syntax.peg.impl.RuleDefs.ELit;
import com.flora.syntax.peg.impl.RuleDefs.ENot;
import com.flora.syntax.peg.impl.RuleDefs.ERef;
import com.flora.syntax.peg.impl.RuleDefs.ERepeat;
import com.flora.syntax.peg.impl.RuleDefs.RuleDef;

import java.util.ArrayList;
import java.util.List;

/**
 * 元解析器：手写递归下降，把一份 g4 子集语法定义字符串解析为 {@link RuleDef} 列表。
 *
 * <p>支持的记法：{@code @start}、{@code fragment}、大写词法规则 / 小写文法规则、{@code |} 候选、
 * {@code '...'} 字面量、{@code [...]}/{@code ~[...]} 字符类、{@code .}、{@code ( )} 分组、
 * {@code * + ?} 重复、{@code &}/{@code !} 前瞻、{@code # Label} 备选标签、{@code -> kind(KIND)}、
 * {@code //} 与 {@code /* *}{@code /} 注释。不支持的 g4 特性（动作 / 谓词 / skip / channel / type / import 等）在此即报错。
 */
public final class MetaParser {

    /** 一次解析的结果：入口规则名 + 规则列表。 */
    public record GrammarDef(String entry, List<RuleDef> rules) {}

    private final String src;
    private int p;
    private int line = 1;
    private int lineStart;
    private String currentMode; // 当前词法模式；null = DEFAULT

    private MetaParser(String src) {
        this.src = src;
    }

    public static GrammarDef parse(String definition) {
        return new MetaParser(definition).parseAll();
    }

    private GrammarDef parseAll() {
        List<RuleDef> rules = new ArrayList<>();
        String entry = null;
        String firstParser = null;
        while (true) {
            skipWs();
            if (p >= src.length()) break;
            if (peekWord("@start")) {
                p += "@start".length();
                String name = ident();
                if (name.isEmpty()) throw err("期望 @start 后的入口规则名");
                expect(';');
                if (entry != null) throw err("重复的 @start");
                entry = name;
                continue;
            }
            // mode NAME; 指令：切换后续词法规则所属的模式（不是规则名为 mode 时回退）
            if (peekWord("mode")) {
                int save = p;
                p += "mode".length();
                skipWs();
                String m = ident();
                if (!m.isEmpty()) {
                    skipWs();
                    if (p < src.length() && src.charAt(p) == ';') {
                        p++;
                        currentMode = m;
                        continue;
                    }
                }
                p = save;
            }
            RuleDef r = parseRule();
            if (!r.lexer() && !r.fragment() && firstParser == null) firstParser = r.name();
            rules.add(r);
        }
        if (entry == null) entry = firstParser;
        if (entry == null) throw new GrammarException("文法未包含任何文法规则（小写开头）");
        return new GrammarDef(entry, rules);
    }

    private RuleDef parseRule() {
        boolean fragment = false;
        if (peekKeyword("fragment")) {
            fragment = true;
            p += "fragment".length();
            skipWs();
        }
        String name = ident();
        if (name.isEmpty()) throw err("期望规则名");
        if (Character.isLowerCase(name.charAt(0)) && !isIdentifier(name)) {
            throw err("非法规则名 '" + name + "'");
        }
        boolean lexer = !name.isEmpty() && Character.isUpperCase(name.charAt(0));
        expect(':');
        List<Alt> alts = parseAlternates();
        skipWs();
        String kind = null;
        String modeAction = null;
        if (p + 1 < src.length() && src.charAt(p) == '-' && src.charAt(p + 1) == '>') {
            p += 2;
            while (true) {
                skipWs();
                String cmd = ident();
                switch (cmd) {
                    case "kind" -> {
                        skipWs();
                        expect('(');
                        String k = ident();
                        if (k.isEmpty()) throw err("期望 kind 名");
                        skipWs();
                        expect(')');
                        kind = k;
                    }
                    case "mode" -> {
                        skipWs();
                        expect('(');
                        String m = ident();
                        if (m.isEmpty()) throw err("期望模式名");
                        skipWs();
                        expect(')');
                        modeAction = "mode:" + m;
                    }
                    case "pushMode" -> {
                        skipWs();
                        expect('(');
                        String m = ident();
                        if (m.isEmpty()) throw err("期望模式名");
                        skipWs();
                        expect(')');
                        modeAction = "pushMode:" + m;
                    }
                    case "popMode" -> {
                        skipWs();
                        if (p < src.length() && src.charAt(p) == '(') {
                            p++;
                            skipWs();
                            expect(')');
                        }
                        modeAction = "popMode";
                    }
                    default -> throw err("不支持的 g4 词法命令 '-> " + cmd
                            + "'（本引擎支持 kind/mode/pushMode/popMode；skip 请用 kind(SKIP)）");
                }
                skipWs();
                if (p < src.length() && src.charAt(p) == ',') {
                    p++;
                    continue;
                }
                break;
            }
        }
        expect(';');
        return new RuleDef(name, lexer, fragment, alts, kind, currentMode, modeAction);
    }

    private List<Alt> parseAlternates() {
        List<Alt> alts = new ArrayList<>();
        alts.add(parseAlternate());
        while (true) {
            skipWs();
            if (p < src.length() && src.charAt(p) == '|') {
                p++;
                alts.add(parseAlternate());
            } else {
                break;
            }
        }
        return alts;
    }

    private Alt parseAlternate() {
        List<Elem> elems = parseElements();
        skipWs();
        String label = null;
        if (p < src.length() && src.charAt(p) == '#') {
            p++;
            label = ident();
            if (label.isEmpty()) throw err("期望 # 后的标签名");
        }
        return new Alt(elems, label);
    }

    private List<Elem> parseElements() {
        List<Elem> elems = new ArrayList<>();
        while (true) {
            skipWs();
            if (p >= src.length()) break;
            char c = src.charAt(p);
            if (c == '|' || c == ')' || c == '#' || c == ';') break;
            if (c == '-' && p + 1 < src.length() && src.charAt(p + 1) == '>') break;
            elems.add(parseElement());
        }
        return elems;
    }

    private Elem parseElement() {
        Elem e = parsePrimary();
        skipWs();
        if (p < src.length()) {
            char c = src.charAt(p);
            if (c == '*') {
                p++;
                e = new ERepeat(e, 0, -1);
            } else if (c == '+') {
                p++;
                e = new ERepeat(e, 1, -1);
            } else if (c == '?') {
                p++;
                e = new ERepeat(e, 0, 1);
            }
        }
        return e;
    }

    private Elem parsePrimary() {
        skipWs();
        char c = src.charAt(p);
        return switch (c) {
            case '\'' -> literal();
            case '(' -> {
                p++;
                List<Alt> alts = parseAlternates();
                skipWs();
                expect(')');
                yield new EGroup(alts);
            }
            case '.' -> {
                p++;
                yield new EAny();
            }
            case '~' -> {
                p++;
                skipWs();
                if (p >= src.length() || src.charAt(p) != '[') throw err("期望 '~[' 取反字符类");
                yield charClass(true);
            }
            case '[' -> charClass(false);
            case '&' -> {
                p++;
                yield new EAnd(parseElement());
            }
            case '!' -> {
                p++;
                yield new ENot(parseElement());
            }
            case '{' -> throw err("嵌入动作 '{...}' 不受支持（本引擎为纯声明，语义请用 visitor 实现）");
            case '}' -> throw err("意外字符 '}'");
            default -> {
                if (Character.isJavaIdentifierStart(c)) {
                    yield new ERef(ident());
                }
                if (peekWord("import")) throw err("'import' 不受支持（本引擎为单串运行时解释器）");
                throw err("意外的字符 '" + c + "'");
            }
        };
    }

    private Elem literal() {
        StringBuilder sb = new StringBuilder();
        p++; // 跳过开引号
        while (p < src.length() && src.charAt(p) != '\'') {
            char c = src.charAt(p);
            if (c == '\\') {
                if (!Esc.isKnown(src, p)) throw err("未知的转义序列");
                int len = Esc.escapeLength(src, p);
                sb.appendCodePoint(Esc.decode(src, p));
                p += len;
            } else {
                sb.append(c);
                p++;
            }
        }
        if (p >= src.length()) throw err("未闭合的字符串字面量");
        p++; // 跳过关引号
        return new ELit(sb.toString());
    }

    private Elem charClass(boolean negated) {
        p++; // 跳过 '['
        StringBuilder sb = new StringBuilder();
        while (p < src.length() && src.charAt(p) != ']') {
            char c = src.charAt(p);
            if (c == '\\') {
                if (!Esc.isKnown(src, p)) throw err("未知的转义序列");
                int len = Esc.escapeLength(src, p);
                sb.append(src, p, p + len);
                p += len;
            } else {
                sb.append(c);
                p++;
            }
        }
        if (p >= src.length()) throw err("未闭合的字符类");
        p++; // 跳过 ']'
        return new EClass(sb.toString(), negated);
    }

    private String ident() {
        skipWs();
        int start = p;
        while (p < src.length() && (Character.isJavaIdentifierPart(src.charAt(p)))) p++;
        return src.substring(start, p);
    }

    private boolean peekWord(String word) {
        return src.startsWith(word, p)
                && (p + word.length() >= src.length() || !Character.isJavaIdentifierPart(src.charAt(p + word.length())));
    }

    private boolean peekKeyword(String word) {
        return src.startsWith(word, p)
                && (p + word.length() < src.length() && Character.isWhitespace(src.charAt(p + word.length())));
    }

    private static boolean isIdentifier(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }

    private void expect(char c) {
        skipWs();
        if (p >= src.length() || src.charAt(p) != c) {
            throw err("期望 '" + c + "'");
        }
        p++;
    }

    private void skipWs() {
        while (p < src.length()) {
            char c = src.charAt(p);
            if (c == '\n') {
                line++;
                lineStart = p + 1;
                p++;
            } else if (c == '\r') {
                p++;
            } else if (c == ' ' || c == '\t') {
                p++;
            } else if (c == '/' && p + 1 < src.length() && src.charAt(p + 1) == '/') {
                while (p < src.length() && src.charAt(p) != '\n') p++;
            } else if (c == '/' && p + 1 < src.length() && src.charAt(p + 1) == '*') {
                p += 2;
                while (p + 1 < src.length() && !(src.charAt(p) == '*' && src.charAt(p + 1) == '/')) {
                    if (src.charAt(p) == '\n') line++;
                    p++;
                }
                if (p + 1 < src.length()) p += 2;
                else throw err("未闭合的块注释");
            } else {
                break;
            }
        }
    }

    private GrammarException err(String msg) {
        int col = p - lineStart + 1;
        return new GrammarException("第 " + line + " 行第 " + col + " 列: " + msg);
    }
}
