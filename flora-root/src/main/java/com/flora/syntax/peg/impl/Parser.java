package com.flora.syntax.peg.impl;

import com.flora.syntax.peg.ParseTree;
import com.flora.syntax.peg.ParseTree.RuleNode;
import com.flora.syntax.peg.ParseTree.TokenNode;
import com.flora.syntax.peg.Token;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文法层解释器：直接对 {@link Elem} AST 做 token 级 PEG 解释（packrat 记忆 + 直接左递归种子生长），
 * 不编译成独立 matcher 类族。token 级匹配自动跳过 Trivia / SKIP（由 {@link RecognizerImpl} 预先过滤）。
 */
final class Parser {

    /** 匹配运行上下文（每次匹配尝试一份）。pos 恒为显著 token 下标。 */
    static final class Run {
        final List<Token> sig;
        final Map<Long, Matched> memo = new HashMap<>();
        final Set<Long> growing = new HashSet<>();
        int furthest = -1;
        final Set<String> expected = new LinkedHashSet<>();

        Run(List<Token> sig) { this.sig = sig; }

        void fail(int pos, String what) {
            if (pos > furthest) {
                furthest = pos;
                expected.clear();
            }
            if (pos == furthest) expected.add(what);
        }

        String expectedText() { return String.join(" / ", expected); }
    }

    /** 匹配结果：捕获的子节点、备选标签、消费的显著 token 数。null 表示失败。 */
    record Matched(List<ParseTree> children, String label, int consumed) {}

    /** 一条文法规则（静态解析表）：候选列表 + 各候选是否为左递归候选。 */
    record ParserRule(String name, int index, List<Alt> alts, boolean[] recAlt) {}

    private final ParserRule[] rules;
    private final int entryIndex;
    private final Map<String, Integer> nameToIndex;

    Parser(ParserRule[] rules, int entryIndex, Map<String, Integer> nameToIndex) {
        this.rules = rules;
        this.entryIndex = entryIndex;
        this.nameToIndex = nameToIndex;
    }

    /** 入口规则调用；成功返回 Matched（含根 RuleNode），失败返回 null。 */
    Matched parse(Run r, int pos) {
        return matchRule(r, rules[entryIndex], pos);
    }

    private Matched matchRule(Run r, ParserRule rule, int pos) {
        long key = ((long) rule.index() << 32) | pos;
        if (r.growing.contains(key)) {
            // 左递归生长期间，自引用返回当前种子（已包装的节点）
            return r.memo.get(key);
        }
        if (r.memo.containsKey(key)) return r.memo.get(key);
        if (hasRecAlt(rule)) return grow(r, rule, pos, key);
        Matched m = runAllAlts(r, rule, pos);
        if (m == null) {
            r.memo.put(key, null);
            return null;
        }
        Matched res = wrap(r, rule, m, pos);
        r.memo.put(key, res);
        return res;
    }

    private Matched grow(Run r, ParserRule rule, int pos, long key) {
        r.growing.add(key);
        Matched seed = runAlts(r, rule, pos, false);
        if (seed == null) {
            r.growing.remove(key);
            r.memo.put(key, null);
            return null;
        }
        Matched best = wrap(r, rule, seed, pos);
        r.memo.put(key, best);
        while (true) {
            Matched next = runAlts(r, rule, pos, true);
            if (next == null || next.consumed() <= best.consumed()) break;
            best = wrap(r, rule, next, pos);
            r.memo.put(key, best);
        }
        r.growing.remove(key);
        r.memo.put(key, best);
        return best;
    }

    private static boolean hasRecAlt(ParserRule rule) {
        for (boolean b : rule.recAlt()) {
            if (b) return true;
        }
        return false;
    }

    /** 非左递归规则的常规路径：按声明顺序尝试全部候选。 */
    private Matched runAllAlts(Run r, ParserRule rule, int pos) {
        List<Alt> alts = rule.alts();
        for (int i = 0; i < alts.size(); i++) {
            Matched m = seqMatch(r, alts.get(i).elems(), pos);
            if (m != null) return new Matched(m.children(), alts.get(i).label(), m.consumed());
        }
        return null;
    }

    /** 左递归种子生长：recursive 为 true 只跑左递归候选，false 只跑种子候选（非左递归）。 */
    private Matched runAlts(Run r, ParserRule rule, int pos, boolean recursive) {
        List<Alt> alts = rule.alts();
        for (int i = 0; i < alts.size(); i++) {
            if (rule.recAlt()[i] != recursive) continue;
            Matched m = seqMatch(r, alts.get(i).elems(), pos);
            if (m != null) return new Matched(m.children(), alts.get(i).label(), m.consumed());
        }
        return null;
    }

    private Matched wrap(Run r, ParserRule rule, Matched m, int pos) {
        Token first = r.sig.get(pos);
        int start = first.start();
        int end = m.consumed() > 0 ? r.sig.get(pos + m.consumed() - 1).end() : start;
        ParseTree node = new RuleNode(rule.name(), m.label(), m.children(), start, end);
        return new Matched(List.of(node), null, m.consumed());
    }

    /** token 级解释器：对单个 Elem 在 pos 处尝试匹配；失败返回 null。 */
    private Matched matchParser(Run r, Elem e, int pos) {
        return switch (e) {
            case ELit lit -> {
                Token t = r.sig.get(pos);
                if (t.text().equals(lit.text())) {
                    yield new Matched(List.of(new TokenNode(t)), null, 1);
                }
                r.fail(pos, "'" + lit.text() + "'");
                yield null;
            }
            case ERef ref -> {
                if (Character.isUpperCase(ref.name().charAt(0))) {
                    Token t = r.sig.get(pos);
                    if (t.typeName().equals(ref.name())) {
                        yield new Matched(List.of(new TokenNode(t)), null, 1);
                    }
                    r.fail(pos, ref.name());
                    yield null;
                }
                Integer idx = nameToIndex.get(ref.name());
                yield idx == null ? null : matchRule(r, rules[idx], pos);
            }
            case EGroup g -> groupMatch(r, g, pos);
            case ERepeat rep -> repeatMatch(r, rep, pos);
            case EAnd a -> matchParser(r, a.elem(), pos) != null ? new Matched(List.of(), null, 0) : null;
            case ENot n -> matchParser(r, n.elem(), pos) == null ? new Matched(List.of(), null, 0) : null;
            case EClass c ->
                    throw new com.flora.syntax.peg.GrammarException("文法层不允许字符类（仅词法层可用）");
            case EAny any ->
                    throw new com.flora.syntax.peg.GrammarException("文法层不允许任意字符 '.'（仅词法层可用）");
        };
    }

    private Matched seqMatch(Run r, List<Elem> elems, int pos) {
        List<ParseTree> children = new ArrayList<>();
        int consumed = 0;
        for (Elem e : elems) {
            Matched m = matchParser(r, e, pos + consumed);
            if (m == null) return null;
            children.addAll(m.children());
            consumed += m.consumed();
        }
        return new Matched(children, null, consumed);
    }

    private Matched groupMatch(Run r, EGroup g, int pos) {
        for (Alt alt : g.alts()) {
            Matched m = seqMatch(r, alt.elems(), pos);
            if (m != null) return m; // 嵌套分组的 label 不影响规则节点
        }
        return null;
    }

    private Matched repeatMatch(Run r, ERepeat rep, int pos) {
        List<ParseTree> children = new ArrayList<>();
        int consumed = 0;
        int count = 0;
        while (rep.max() < 0 || count < rep.max()) {
            Matched m = matchParser(r, rep.elem(), pos + consumed);
            if (m == null || m.consumed() == 0) break;
            children.addAll(m.children());
            consumed += m.consumed();
            count++;
        }
        if (count < rep.min()) return null;
        return new Matched(children, null, consumed);
    }
}
