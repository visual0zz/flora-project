package com.flora.syntax.peg.impl;

import com.flora.syntax.definition.TokenKind;
import com.flora.syntax.peg.GrammarOptions;
import com.flora.syntax.peg.impl.MetaParser.GrammarDef;
import com.flora.syntax.peg.impl.RuleDefs.Alt;
import com.flora.syntax.peg.impl.RuleDefs.EAnd;
import com.flora.syntax.peg.impl.RuleDefs.EGroup;
import com.flora.syntax.peg.impl.RuleDefs.Elem;
import com.flora.syntax.peg.impl.RuleDefs.ELit;
import com.flora.syntax.peg.impl.RuleDefs.ENot;
import com.flora.syntax.peg.impl.RuleDefs.ERef;
import com.flora.syntax.peg.impl.RuleDefs.ERepeat;
import com.flora.syntax.peg.impl.RuleDefs.RuleDef;
import com.flora.syntax.peg.impl.Parser.ParserRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编译器：把校验过的 {@link RuleDef} 编译为静态解析表——词法器（TokenRule 存 Elem body）+ 文法规则表
 * （{@link ParserRule}），运行期由 {@link Lexer} / {@link Parser} 直接解释 Elem AST。
 */
public final class Compiler {

    /** 编译产物（静态、不可变）：词法器规格 + 文法规则表 + 两个解释器 + 是否自动跳过 trivia/SKIP。 */
    public record CompiledGrammar(String entry,
                                  List<Lexer.TokenRule> tokenRules,
                                  Lexer lexer,
                                  Parser parser,
                                  boolean autoSkip) {}

    private final GrammarOptions opts;
    private List<RuleDef> rules;
    private Map<String, Integer> lexerMap;
    private Map<String, Integer> parserMap;

    private final List<Lexer.TokenRule> tokenRules = new ArrayList<>();
    private final Map<String, Integer> tokenIndex = new LinkedHashMap<>();

    public Compiler(GrammarOptions opts) {
        this.opts = opts;
    }

    public CompiledGrammar compile(GrammarDef def, Validator.Validation v) {
        this.rules = def.rules();
        this.lexerMap = v.lexerMap();
        this.parserMap = v.parserMap();

        // 1. 词法规则体（含 fragment），供词法器 ERef 引用与 TokenRule 使用
        Map<String, Elem> lexerBodies = new HashMap<>();
        for (RuleDef r : rules) {
            if (r.lexer() || r.fragment()) lexerBodies.put(r.name(), new EGroup(r.alts()));
        }
        // 2. 具名词法 token（fragment 不产 token）
        for (RuleDef r : rules) {
            if (r.lexer() && !r.fragment()) {
                tokenIndex.put(r.name(), tokenRules.size());
                tokenRules.add(new Lexer.TokenRule(r.name(), resolveKind(r),
                        r.mode(), r.modeAction(), lexerBodies.get(r.name())));
            }
        }
        // 3. 文法规则里的字符串字面量 → 隐式 token（kind=Terminal，名取字面量文本，任意模式可匹配）
        collectImplicitLiterals();

        // 4. 文法规则表 + 左递归候选标记
        ParserRule[] parserRules = new ParserRule[rules.size()];
        for (RuleDef r : rules) {
            if (r.lexer() || r.fragment()) continue;
            int idx = parserMap.get(r.name());
            boolean[] rec = new boolean[r.alts().size()];
            for (int i = 0; i < rec.length; i++) {
                rec[i] = altRecursive(r.alts().get(i).elems(), r.name());
            }
            parserRules[idx] = new ParserRule(r.name(), idx, r.alts(), rec);
        }

        int entryIdx = parserMap.get(def.entry());
        Parser parser = new Parser(parserRules, entryIdx, parserMap);
        Lexer lexer = new Lexer(tokenRules, opts.lexerLongestMatch(), opts.caseInsensitive(), lexerBodies);
        return new CompiledGrammar(def.entry(), tokenRules, lexer, parser, opts.autoSkip());
    }

    private void collectImplicitLiterals() {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (RuleDef r : rules) {
            if (r.lexer() || r.fragment()) continue;
            for (Alt alt : r.alts()) {
                for (Elem e : alt.elems()) collectLit(e, seen);
            }
        }
        for (Map.Entry<String, Boolean> e : seen.entrySet()) {
            if (!tokenIndex.containsKey(e.getKey())) {
                tokenIndex.put(e.getKey(), tokenRules.size());
                tokenRules.add(new Lexer.TokenRule(e.getKey(),
                        TokenKind.TERMINAL, null, null, new ELit(e.getKey())));
            }
        }
    }

    private void collectLit(Elem e, Map<String, Boolean> seen) {
        switch (e) {
            case ELit lit -> seen.put(lit.text(), Boolean.TRUE);
            case EGroup g -> g.alts().forEach(a -> a.elems().forEach(x -> collectLit(x, seen)));
            case ERepeat rep -> collectLit(rep.elem(), seen);
            case EAnd a -> collectLit(a.elem(), seen);
            case ENot n -> collectLit(n.elem(), seen);
            default -> { }
        }
    }

    /** 解析规则上的 {@code -> kind(KIND)}；未标注一律 CUSTOM（无猜测）。 */
    private TokenKind resolveKind(RuleDef r) {
        return r.kindName() != null ? TokenKind.of(r.kindName()) : TokenKind.CUSTOM;
    }

    /** 该候选是否在首位置引用自身（直接左递归）。 */
    private boolean altRecursive(List<Elem> elems, String ruleName) {
        for (Elem e : elems) {
            switch (e) {
                case EAnd a -> { /* 零宽，继续 */ }
                case ENot n -> { /* 零宽，继续 */ }
                case ERepeat rep -> {
                    if (altRecursive(List.of(rep.elem()), ruleName)) return true;
                    if (rep.min() != 0) return false;
                }
                case EGroup g -> {
                    if (groupStartsWith(g, ruleName)) return true;
                    if (!RuleDefs.nullableFirst(g)) return false; // 分组可空则继续看下一个元素
                }
                case ERef ref -> { return ref.name().equals(ruleName); }
                default -> { return false; }
            }
        }
        return false;
    }

    private boolean groupStartsWith(EGroup g, String ruleName) {
        for (Alt a : g.alts()) {
            if (altRecursive(a.elems(), ruleName)) return true;
        }
        return false;
    }
}
