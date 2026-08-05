package com.flora.syntax.peg.impl;

import com.flora.syntax.definition.Token;
import com.flora.syntax.definition.TokenKind;
import com.flora.syntax.exceptions.ParseException;
import com.flora.syntax.peg.impl.RuleDefs.Alt;
import com.flora.syntax.peg.impl.RuleDefs.EAnd;
import com.flora.syntax.peg.impl.RuleDefs.EAny;
import com.flora.syntax.peg.impl.RuleDefs.EClass;
import com.flora.syntax.peg.impl.RuleDefs.EGroup;
import com.flora.syntax.peg.impl.RuleDefs.Elem;
import com.flora.syntax.peg.impl.RuleDefs.ELit;
import com.flora.syntax.peg.impl.RuleDefs.ENot;
import com.flora.syntax.peg.impl.RuleDefs.ERef;
import com.flora.syntax.peg.impl.RuleDefs.ERepeat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 词法器：解释器风格——直接对 {@link Elem} AST 做字符级解释，不编译成独立 matcher 类族。
 *
 * <p>支持最长匹配（平局按声明顺序）、词法模式（mode / pushMode / popMode）、隐式字面量 token
 * （mode 为 null 时任意模式可匹配）。把输入切为全部 token（含 {@code kind=SKIP} 的）。
 */
final class Lexer {

    /** 一条 token 规格：名称 + 类别 + 所属模式 + 模式命令 + 规则体（AST，直接解释）。 */
    record TokenRule(String name, TokenKind kind, String mode, String modeAction, Elem body) {
        boolean matchesMode(String current) {
            return mode == null || mode.equals(current);
        }
    }

    private final List<TokenRule> rules;
    private final boolean longestMatch;
    private final boolean ci;
    private final Map<String, Elem> lexerBodies;
    private final Map<EClass, int[][]> classCache = new HashMap<>();

    Lexer(List<TokenRule> rules, boolean longestMatch, boolean caseInsensitive, Map<String, Elem> lexerBodies) {
        this.rules = rules;
        this.longestMatch = longestMatch;
        this.ci = caseInsensitive;
        this.lexerBodies = lexerBodies;
    }

    /** 切词，返回全部 token（含 EOF 哨兵）。无规则匹配的字符即抛词法错误（带位置）。 */
    List<Token> lex(CharSequence in) {
        List<Token> out = new ArrayList<>();
        ArrayDeque<String> modeStack = new ArrayDeque<>();
        String mode = "DEFAULT";
        int pos = 0;
        int line = 1;
        int col = 1;
        while (pos < in.length()) {
            TokenRule best = null;
            int bestLen = -1;
            for (TokenRule tr : rules) {
                if (!tr.matchesMode(mode)) continue;
                int len = matchLexer(tr.body(), in, pos);
                if (len < 0) continue;
                if (longestMatch) {
                    if (len > bestLen) {
                        best = tr;
                        bestLen = len;
                    }
                } else {
                    best = tr;
                    bestLen = len;
                    break;
                }
            }
            if (best == null) {
                throw new ParseException("无法识别的字符 '" + in.charAt(pos) + "'", line, col, pos, "<lexer>");
            }
            String text = in.subSequence(pos, pos + bestLen).toString();
            out.add(new Token(best.kind(), best.name(), text, pos, pos + bestLen, line, col));
            String act = best.modeAction();
            if (act != null) {
                if (act.startsWith("mode:")) {
                    mode = act.substring(5);
                } else if (act.startsWith("pushMode:")) {
                    modeStack.push(mode);
                    mode = act.substring(9);
                } else if (act.equals("popMode")) {
                    if (!modeStack.isEmpty()) mode = modeStack.pop();
                }
            }
            for (int k = 0; k < bestLen; k++) {
                if (text.charAt(k) == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
            pos += bestLen;
        }
        out.add(new Token(new TokenKind.Eof(), "EOF", "", pos, pos, line, col));
        return out;
    }

    /** 字符级解释器：对单个 Elem 在 pos 处尝试匹配，返回匹配长度；失败返回 -1。 */
    private int matchLexer(Elem e, CharSequence s, int pos) {
        return switch (e) {
            case ELit lit -> litMatch(lit.text(), s, pos);
            case EClass c -> classMatch(c, s, pos);
            case EAny any -> pos < s.length() ? 1 : -1;
            case ERef ref -> matchLexer(lexerBodies.get(ref.name()), s, pos);
            case EGroup g -> groupMatch(g, s, pos);
            case ERepeat rep -> repeatMatch(rep, s, pos);
            case EAnd a -> matchLexer(a.elem(), s, pos) >= 0 ? 0 : -1;
            case ENot n -> matchLexer(n.elem(), s, pos) < 0 ? 0 : -1;
        };
    }

    private int litMatch(String text, CharSequence s, int pos) {
        if (pos + text.length() > s.length()) return -1;
        for (int i = 0; i < text.length(); i++) {
            char a = text.charAt(i);
            char b = s.charAt(pos + i);
            if (a != b && (!ci || Character.toLowerCase(a) != Character.toLowerCase(b))) return -1;
        }
        return text.length();
    }

    private int classMatch(EClass c, CharSequence s, int pos) {
        if (pos >= s.length()) return -1;
        char ch = s.charAt(pos);
        int[][] ranges = classCache.computeIfAbsent(c, k -> CharClass.parseRanges(k.inner()));
        boolean in = CharClass.inRanges(ranges, ch);
        if (ci && !in) {
            in = CharClass.inRanges(ranges, Character.toLowerCase(ch))
                    || CharClass.inRanges(ranges, Character.toUpperCase(ch));
        }
        boolean ok = c.negated() ? !in : in;
        return ok ? 1 : -1;
    }

    private int groupMatch(EGroup g, CharSequence s, int pos) {
        for (Alt alt : g.alts()) {
            int len = seqMatch(alt.elems(), s, pos);
            if (len >= 0) return len;
        }
        return -1;
    }

    private int seqMatch(List<Elem> elems, CharSequence s, int pos) {
        int p = pos;
        for (Elem e : elems) {
            int len = matchLexer(e, s, p);
            if (len < 0) return -1;
            p += len;
        }
        return p - pos;
    }

    private int repeatMatch(ERepeat rep, CharSequence s, int pos) {
        int p = pos;
        int count = 0;
        while (rep.max() < 0 || count < rep.max()) {
            int len = matchLexer(rep.elem(), s, p);
            if (len <= 0) break; // 防零宽死循环
            p += len;
            count++;
        }
        return count < rep.min() ? -1 : p - pos;
    }
}
