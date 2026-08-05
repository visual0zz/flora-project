package com.flora.syntax.peg.impl;

import com.flora.syntax.definition.TokenKind;
import com.flora.syntax.exceptions.SyntaxException;
import com.flora.syntax.peg.impl.MetaParser.GrammarDef;
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
import com.flora.syntax.peg.impl.RuleDefs.RuleDef;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 校验器：引用解析、词法/文法分层约束、词法规则非空串、词法引用图无环、文法左递归检测（MVP 报错，
 * 阶段 10 转支持）、{@code -> kind} 词汇表校验。
 */
public final class Validator {

    public record Validation(Map<String, Integer> lexerMap, Map<String, Integer> parserMap) {}

    public static Validation validate(GrammarDef def) {
        return new Validator().run(def);
    }

    private Validation run(GrammarDef def) {
        List<RuleDef> rules = def.rules();
        Map<String, Integer> lexerMap = new HashMap<>();
        Map<String, Integer> parserMap = new HashMap<>();
        for (int i = 0; i < rules.size(); i++) {
            RuleDef r = rules.get(i);
            if (r.lexer() || r.fragment()) {
                if (lexerMap.put(r.name(), i) != null) throw dup(r.name());
            } else {
                if (parserMap.put(r.name(), i) != null) throw dup(r.name());
            }
        }
        if (!parserMap.containsKey(def.entry())) {
            throw new SyntaxException("入口规则 '" + def.entry() + "' 未定义或不是文法规则（小写开头）");
        }

        for (RuleDef r : rules) {
            checkRefs(r, rules, lexerMap, parserMap);
            if (r.kindName() != null) {
                TokenKind k = TokenKind.of(r.kindName());
                if (k == null) throw new SyntaxException("未知的 kind '" + r.kindName() + "'");
                if (k instanceof TokenKind.Eof) {
                    throw new SyntaxException("kind(EOF) 为引擎结束哨兵，作者不可选");
                }
            }
        }

        checkLexerAcyclic(rules, lexerMap);
        checkLexerNotNullable(rules, lexerMap);
        checkLeftRecursion(rules, parserMap);
        checkModes(rules);
        return new Validation(lexerMap, parserMap);
    }

    private void checkRefs(RuleDef r, List<RuleDef> rules,
                           Map<String, Integer> lexerMap, Map<String, Integer> parserMap) {
        boolean lexerCtx = r.lexer() || r.fragment();
        for (Alt alt : r.alts()) {
            for (Elem e : alt.elems()) {
                checkRefElem(r, e, lexerCtx, rules, lexerMap, parserMap);
            }
        }
    }

    private void checkRefElem(RuleDef owner, Elem e, boolean lexerCtx, List<RuleDef> rules,
                              Map<String, Integer> lexerMap, Map<String, Integer> parserMap) {
        switch (e) {
            case ERef ref -> {
                String name = ref.name();
                boolean upper = Character.isUpperCase(name.charAt(0));
                if (upper) {
                    Integer idx = lexerMap.get(name);
                    if (idx == null) {
                        throw new SyntaxException("规则 '" + owner.name() + "' 引用了未定义的 token '" + name + "'");
                    }
                    if (!lexerCtx && rules.get(idx).fragment()) {
                        throw new SyntaxException("文法规则 '" + owner.name() + "' 不能引用 fragment '" + name + "'");
                    }
                } else {
                    if (lexerCtx) {
                        throw new SyntaxException("词法规则 '" + owner.name() + "' 不能引用文法规则 '" + name + "'");
                    }
                    if (!parserMap.containsKey(name)) {
                        throw new SyntaxException("规则 '" + owner.name() + "' 引用了未定义的文法规则 '" + name + "'");
                    }
                }
            }
            case EGroup g -> {
                for (Alt alt : g.alts()) {
                    for (Elem ee : alt.elems()) checkRefElem(owner, ee, lexerCtx, rules, lexerMap, parserMap);
                }
            }
            case ERepeat rep -> checkRefElem(owner, rep.elem(), lexerCtx, rules, lexerMap, parserMap);
            case EAnd a -> checkRefElem(owner, a.elem(), lexerCtx, rules, lexerMap, parserMap);
            case ENot n -> checkRefElem(owner, n.elem(), lexerCtx, rules, lexerMap, parserMap);
            default -> { /* ELit / EClass / EAny 无需检查 */ }
        }
    }

    private void checkLexerAcyclic(List<RuleDef> rules, Map<String, Integer> lexerMap) {
        int n = rules.size();
        int[] state = new int[n]; // 0 未访问, 1 访问中, 2 完成
        for (Integer idx : lexerMap.values()) {
            dfsLexer(rules, lexerMap, idx, state, new ArrayDeque<>());
        }
    }

    private void dfsLexer(List<RuleDef> rules, Map<String, Integer> lexerMap, int idx,
                          int[] state, ArrayDeque<String> stack) {
        if (state[idx] == 2) return;
        if (state[idx] == 1) {
            throw new SyntaxException("词法规则引用成环: " + stack.peekLast() + " → " + rules.get(idx).name());
        }
        state[idx] = 1;
        stack.addLast(rules.get(idx).name());
        for (ERef ref : lexerRefs(rules.get(idx))) {
            Integer t = lexerMap.get(ref.name());
            if (t != null) dfsLexer(rules, lexerMap, t, state, stack);
        }
        stack.removeLast();
        state[idx] = 2;
    }

    private static java.util.List<ERef> lexerRefs(RuleDef r) {
        java.util.List<ERef> out = new java.util.ArrayList<>();
        for (Alt alt : r.alts()) {
            for (Elem e : alt.elems()) collectRefs(e, out);
        }
        return out;
    }

    private static void collectRefs(Elem e, java.util.List<ERef> out) {
        switch (e) {
            case ERef ref -> out.add(ref);
            case EGroup g -> g.alts().forEach(a -> a.elems().forEach(x -> collectRefs(x, out)));
            case ERepeat rep -> collectRefs(rep.elem(), out);
            case EAnd a -> collectRefs(a.elem(), out);
            case ENot n -> collectRefs(n.elem(), out);
            default -> { }
        }
    }

    private void checkLexerNotNullable(List<RuleDef> rules, Map<String, Integer> lexerMap) {
        Map<Integer, Boolean> memo = new HashMap<>();
        for (Map.Entry<String, Integer> e : lexerMap.entrySet()) {
            RuleDef r = rules.get(e.getValue());
            if (!r.fragment() && isNullable(r, rules, lexerMap, memo)) {
                throw new SyntaxException("词法规则 '" + r.name() + "' 可匹配空串（会导致零宽 token 使词法器死循环）");
            }
        }
    }

    private boolean isNullable(RuleDef r, List<RuleDef> rules, Map<String, Integer> lexerMap, Map<Integer, Boolean> memo) {
        Integer idx = lexerMap.get(r.name());
        Boolean cached = idx != null ? memo.get(idx) : null;
        if (cached != null) return cached;
        if (idx != null) memo.put(idx, false); // 防环（图已验无环，此仅为保险）
        boolean any = false;
        for (Alt alt : r.alts()) {
            boolean all = true;
            for (Elem e : alt.elems()) {
                if (!elemNullable(e, rules, lexerMap, memo)) { all = false; break; }
            }
            if (all) { any = true; break; }
        }
        if (idx != null) memo.put(idx, any);
        return any;
    }

    private boolean elemNullable(Elem e, List<RuleDef> rules, Map<String, Integer> lexerMap, Map<Integer, Boolean> memo) {
        return switch (e) {
            case ELit lit -> lit.text().isEmpty();
            case EClass c -> false;
            case EAny a -> false;
            case ERef ref -> {
                Integer i = lexerMap.get(ref.name());
                yield i != null && isNullable(rules.get(i), rules, lexerMap, memo);
            }
            case EGroup g -> {
                boolean any = false;
                for (Alt a : g.alts()) {
                    boolean all = true;
                    for (Elem ee : a.elems()) {
                        if (!elemNullable(ee, rules, lexerMap, memo)) { all = false; break; }
                    }
                    if (all) { any = true; break; }
                }
                yield any;
            }
            case ERepeat rep -> rep.min() == 0;
            case EAnd a -> true;
            case ENot n -> true;
        };
    }

    private void checkLeftRecursion(List<RuleDef> rules, Map<String, Integer> parserMap) {
        int n = rules.size();
        boolean[][] first = new boolean[n][n];
        for (Map.Entry<String, Integer> e : parserMap.entrySet()) {
            RuleDef r = rules.get(e.getValue());
            for (Alt alt : r.alts()) {
                collectFirst(alt.elems(), rules, parserMap, e.getValue(), first);
            }
        }
        // 1) 间接左递归（去掉自环后仍有环，如 a -> b -> a）→ 拒绝（仅支持直接左递归）
        boolean[][] indirect = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) indirect[i][j] = first[i][j] && i != j;
        }
        if (anyCycle(indirect)) {
            throw new SyntaxException("间接左递归（PEG 死循环）不受支持，请改写为直接左递归或引入优先级分层");
        }
        // 2) 直接左递归规则必须有非左递归的种子候选（Warth 种子生长所需）
        for (Map.Entry<String, Integer> e : parserMap.entrySet()) {
            int i = e.getValue();
            if (first[i][i]) {
                RuleDef r = rules.get(i);
                boolean hasSeed = r.alts().stream().anyMatch(a -> !altStartsWithSelf(a.elems(), r.name()));
                if (!hasSeed) {
                    throw new SyntaxException("左递归规则 '" + r.name() + "' 缺少非左递归的种子候选（如 term 之于 expr : expr '+' term | term）");
                }
            }
        }
    }

    /** 该候选是否在首位置引用自身（直接左递归候选）。 */
    private boolean altStartsWithSelf(List<Elem> elems, String ruleName) {
        for (Elem e : elems) {
            switch (e) {
                case EAnd a -> { /* 零宽，继续 */ }
                case ENot n -> { /* 零宽，继续 */ }
                case ERepeat rep -> {
                    if (altStartsWithSelf(List.of(rep.elem()), ruleName)) return true;
                    if (rep.min() != 0) return false;
                }
                case EGroup g -> {
                    if (groupStartsWithSelf(g, ruleName)) return true;
                    if (!RuleDefs.nullableFirst(g)) return false; // 分组可空则继续看下一个元素
                }
                case ERef ref -> { return ref.name().equals(ruleName); }
                default -> { return false; }
            }
        }
        return false;
    }

    private boolean groupStartsWithSelf(EGroup g, String ruleName) {
        for (Alt a : g.alts()) {
            if (altStartsWithSelf(a.elems(), ruleName)) return true;
        }
        return false;
    }

    private void collectFirst(List<Elem> elems, List<RuleDef> rules, Map<String, Integer> parserMap,
                              int self, boolean[][] first) {
        for (Elem e : elems) {
            switch (e) {
                case EAnd a -> { /* 零宽，继续看下一个 */ }
                case ENot n -> { /* 零宽，继续看下一个 */ }
                case ERepeat rep -> {
                    if (rep.min() == 0) {
                        // 可能被跳过；同时其内部可出现在首位
                        collectFirst(List.of(rep.elem()), rules, parserMap, self, first);
                    } else {
                        collectFirst(List.of(rep.elem()), rules, parserMap, self, first);
                        return;
                    }
                }
                case EGroup g -> {
                    boolean groupNullable = false;
                    for (Alt a : g.alts()) {
                        collectFirst(a.elems(), rules, parserMap, self, first);
                        if (a.elems().stream().allMatch(x -> elemNullableForFirst(x, rules, parserMap))) {
                            groupNullable = true; // 任一候选可空 → 分组可空 → 后续元素仍在首位置
                        }
                    }
                    if (!groupNullable) return;
                }
                case ERef ref -> {
                    Integer t = parserMap.get(ref.name());
                    if (t != null) first[self][t] = true;
                    // 若该规则可空，继续看下一个
                    RuleDef rd = parserMap.containsKey(ref.name()) ? rules.get(parserMap.get(ref.name())) : null;
                    if (rd == null || !parserRuleNullable(rd, rules, parserMap)) return;
                }
                default -> { /* 字面量/字符类/token 引用都会消费，停止 */ return; }
            }
        }
    }

    /** 图中是否存在环（onPath 深度优先检测）。 */
    private boolean anyCycle(boolean[][] g) {
        int n = g.length;
        boolean[] onPath = new boolean[n];
        boolean[] visited = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!visited[i] && cycleDfs(i, g, onPath, visited)) return true;
        }
        return false;
    }

    private boolean cycleDfs(int v, boolean[][] g, boolean[] onPath, boolean[] visited) {
        visited[v] = true;
        onPath[v] = true;
        for (int j = 0; j < g.length; j++) {
            if (g[v][j]) {
                if (onPath[j]) return true;
                if (!visited[j] && cycleDfs(j, g, onPath, visited)) return true;
            }
        }
        onPath[v] = false;
        return false;
    }

    private boolean elemNullableForFirst(Elem e, List<RuleDef> rules, Map<String, Integer> parserMap) {
        return switch (e) {
            case EAnd a -> true;
            case ENot n -> true;
            case ERepeat rep -> rep.min() == 0 || elemNullableForFirst(rep.elem(), rules, parserMap);
            case EGroup g -> g.alts().stream()
                    .anyMatch(a -> a.elems().stream().allMatch(x -> elemNullableForFirst(x, rules, parserMap)));
            case ERef ref -> parserMap.containsKey(ref.name()) && parserRuleNullable(rules.get(parserMap.get(ref.name())), rules, parserMap);
            default -> false;
        };
    }

    private final Set<Integer> nullInProgress = new java.util.HashSet<>();

    private boolean parserRuleNullable(RuleDef r, List<RuleDef> rules, Map<String, Integer> parserMap) {
        Integer idx = parserMap.get(r.name());
        // 环上（左递归）保守视为不可空，避免无限递归
        if (idx != null && !nullInProgress.add(idx)) return false;
        try {
            for (Alt alt : r.alts()) {
                boolean all = true;
                for (Elem e : alt.elems()) {
                    if (!elemNullableForFirst(e, rules, parserMap)) { all = false; break; }
                }
                if (all) return true;
            }
            return false;
        } finally {
            if (idx != null) nullInProgress.remove(idx);
        }
    }

    /** 校验词法模式引用：{@code mode:} / {@code pushMode:} 的目标必须是已声明的模式。 */
    private void checkModes(List<RuleDef> rules) {
        Set<String> modes = new java.util.HashSet<>();
        modes.add("DEFAULT");
        for (RuleDef r : rules) {
            if (r.mode() != null) modes.add(r.mode());
        }
        for (RuleDef r : rules) {
            String a = r.modeAction();
            if (a == null) continue;
            if (a.startsWith("mode:")) {
                String m = a.substring(5);
                if (!modes.contains(m)) throw new SyntaxException("未定义的模式 '" + m + "'");
            } else if (a.startsWith("pushMode:")) {
                String m = a.substring(9);
                if (!modes.contains(m)) throw new SyntaxException("未定义的模式 '" + m + "'");
            }
        }
    }

    private static SyntaxException dup(String name) {
        return new SyntaxException("重复定义的规则 '" + name + "'");
    }
}
