package com.flora.syntax.peg.impl;

import com.flora.syntax.peg.ParseTree;
import com.flora.syntax.peg.ParseTree.RuleNode;
import com.flora.syntax.peg.ParseTree.TokenNode;
import com.flora.syntax.peg.Token;
import com.flora.syntax.peg.TokenKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文法层：token 级 PEG 匹配器（在显著 token 流上运行，自动跳过 Trivia / SKIP）。
 *
 * <p>packrat 记忆键为 (ruleIndex, pos)。规则引用成功时构建 {@link RuleNode}；token / 字面量匹配
 * 构建 {@link TokenNode}。失败记录最远失败位置与期望项，供 {@link com.flora.syntax.peg.ParseException} 使用。
 */
final class Matchers {

    /** 匹配运行上下文：显著 token 流 + packrat 记忆 + 错误记录。pos 恒为显著 token 下标。 */
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

    /** 规则体：有序候选的集合。编译期先构造空壳（供互相引用），再 fill 填充。 */
    static final class RuleBody {
        final String name;
        final int index;
        Matcher[] alts;
        String[] labels;
        boolean[] recAlt; // 该候选是否为左递归候选（首位置引用自身）

        RuleBody(String name, int index) {
            this.name = name;
            this.index = index;
        }

        void fill(Matcher[] alts, String[] labels, boolean[] recAlt) {
            this.alts = alts;
            this.labels = labels;
            this.recAlt = recAlt;
        }
    }

    abstract static class Matcher {
        abstract Matched run(Run r, int pos);
    }

    /** 匹配一个指定词法规则名的 token。 */
    static final class TokenMatch extends Matcher {
        final String typeName;
        TokenMatch(String typeName) { this.typeName = typeName; }
        @Override
        Matched run(Run r, int pos) {
            Token t = r.sig.get(pos);
            if (t.typeName().equals(typeName)) {
                return new Matched(List.of(new TokenNode(t)), null, 1);
            }
            r.fail(pos, typeName);
            return null;
        }
    }

    /** 匹配一个文本等于字面量的 token（隐式 token / 文法内联字面量）。 */
    static final class LiteralMatch extends Matcher {
        final String text;
        LiteralMatch(String text) { this.text = text; }
        @Override
        Matched run(Run r, int pos) {
            Token t = r.sig.get(pos);
            if (t.text().equals(text)) {
                return new Matched(List.of(new TokenNode(t)), null, 1);
            }
            r.fail(pos, "'" + text + "'");
            return null;
        }
    }

    /** 顺序组合（子节点平铺到父级）。 */
    static final class Seq extends Matcher {
        final Matcher[] parts;
        Seq(Matcher[] parts) { this.parts = parts; }
        @Override
        Matched run(Run r, int pos) {
            List<ParseTree> children = new ArrayList<>();
            int consumed = 0;
            for (Matcher p : parts) {
                Matched m = p.run(r, pos + consumed);
                if (m == null) return null;
                children.addAll(m.children());
                consumed += m.consumed();
            }
            return new Matched(children, null, consumed);
        }
    }

    /** 有序选择（首个命中）。 */
    static final class Choice extends Matcher {
        final Matcher[] parts;
        Choice(Matcher[] parts) { this.parts = parts; }
        @Override
        Matched run(Run r, int pos) {
            for (Matcher p : parts) {
                Matched m = p.run(r, pos);
                if (m != null) return m;
            }
            return null;
        }
    }

    /** 贪婪重复；max == -1 无上限；内部零宽时停止。 */
    static final class Repeat extends Matcher {
        final Matcher inner;
        final int min;
        final int max;
        Repeat(Matcher inner, int min, int max) { this.inner = inner; this.min = min; this.max = max; }
        @Override
        Matched run(Run r, int pos) {
            List<ParseTree> children = new ArrayList<>();
            int consumed = 0;
            int count = 0;
            while (max < 0 || count < max) {
                Matched m = inner.run(r, pos + consumed);
                if (m == null || m.consumed() == 0) break;
                children.addAll(m.children());
                consumed += m.consumed();
                count++;
            }
            if (count < min) return null;
            return new Matched(children, null, consumed);
        }
    }

    /** 前瞻：内部匹配则成功且不消费。 */
    static final class And extends Matcher {
        final Matcher inner;
        And(Matcher inner) { this.inner = inner; }
        @Override
        Matched run(Run r, int pos) {
            return inner.run(r, pos) != null ? new Matched(List.of(), null, 0) : null;
        }
    }

    /** 负前瞻：内部不匹配则成功且不消费。 */
    static final class Not extends Matcher {
        final Matcher inner;
        Not(Matcher inner) { this.inner = inner; }
        @Override
        Matched run(Run r, int pos) {
            return inner.run(r, pos) == null ? new Matched(List.of(), null, 0) : null;
        }
    }

    /** 规则引用（packrat 记忆；左递归规则走种子生长）。 */
    static final class RuleRef extends Matcher {
        final RuleBody body;
        final boolean leftRecursive;
        RuleRef(RuleBody body, boolean leftRecursive) {
            this.body = body;
            this.leftRecursive = leftRecursive;
        }
        @Override
        Matched run(Run r, int pos) {
            long key = ((long) body.index << 32) | pos;
            if (r.growing.contains(key)) {
                // 左递归生长期间，自引用返回当前种子（已包装的节点）
                return r.memo.get(key);
            }
            if (r.memo.containsKey(key)) return r.memo.get(key);
            if (leftRecursive) return grow(r, pos, key);
            Matched m = runAllAlts(r, pos);
            if (m == null) {
                r.memo.put(key, null);
                return null;
            }
            Matched res = wrap(r, body, m, pos);
            r.memo.put(key, res);
            return res;
        }

        /** 非左递归规则的常规路径：按声明顺序尝试全部候选。 */
        private Matched runAllAlts(Run r, int pos) {
            for (int i = 0; i < body.alts.length; i++) {
                Matched m = body.alts[i].run(r, pos);
                if (m != null) return new Matched(m.children(), body.labels[i], m.consumed());
            }
            return null;
        }

        private Matched grow(Run r, int pos, long key) {
            r.growing.add(key);
            Matched seed = runAlts(r, pos, false);
            if (seed == null) {
                r.growing.remove(key);
                r.memo.put(key, null);
                return null;
            }
            Matched best = wrap(r, body, seed, pos);
            r.memo.put(key, best);
            while (true) {
                Matched next = runAlts(r, pos, true);
                if (next == null || next.consumed() <= best.consumed()) break;
                best = wrap(r, body, next, pos);
                r.memo.put(key, best);
            }
            r.growing.remove(key);
            r.memo.put(key, best);
            return best;
        }

        private Matched runAlts(Run r, int pos, boolean recursive) {
            for (int i = 0; i < body.alts.length; i++) {
                if (body.recAlt[i] != recursive) continue;
                Matched m = body.alts[i].run(r, pos);
                if (m != null) return new Matched(m.children(), body.labels[i], m.consumed());
            }
            return null;
        }

        private Matched wrap(Run r, RuleBody body, Matched m, int pos) {
            Token first = r.sig.get(pos);
            int start = first.start();
            int end = m.consumed() > 0 ? r.sig.get(pos + m.consumed() - 1).end() : start;
            ParseTree node = new RuleNode(body.name, m.label(), m.children(), start, end);
            return new Matched(List.of(node), null, m.consumed());
        }
    }
}
