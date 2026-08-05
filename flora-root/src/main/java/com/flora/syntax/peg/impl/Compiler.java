package com.flora.syntax.peg.impl;

import com.flora.syntax.peg.GrammarOptions;
import com.flora.syntax.peg.TokenKind;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编译器：把校验过的 {@link RuleDef} 编译为内存高效形态——词法器（CharMatcher 列表 + 隐式 token）
 * + token 级 PEG（{@link Matchers.RuleBody} 树）。含直接左递归的规则标记为种子生长。
 */
public final class Compiler {

    /** 编译产物。 */
    public record CompiledGrammar(String entry,
                                  List<Lexer.TokenRule> tokenRules,
                                  Lexer lexer,
                                  Matchers.RuleBody[] ruleBodies,
                                  Matchers.RuleRef entryMatcher) {}

    private final GrammarOptions opts;
    private List<RuleDef> rules;
    private Map<String, Integer> lexerMap;
    private Map<String, Integer> parserMap;

    private final Map<String, CharMatcher> lexerCompiled = new HashMap<>();
    private final List<Lexer.TokenRule> tokenRules = new ArrayList<>();
    private final Map<String, Integer> tokenIndex = new LinkedHashMap<>();
    private java.util.Set<String> referencedTokens = new java.util.HashSet<>();

    private Matchers.RuleBody[] ruleBodies;
    private boolean[] leftRec;

    public Compiler(GrammarOptions opts) {
        this.opts = opts;
    }

    public CompiledGrammar compile(GrammarDef def, Validator.Validation v) {
        this.rules = def.rules();
        this.lexerMap = v.lexerMap();
        this.parserMap = v.parserMap();

        // 收集被文法规则引用的 token 名：被引用即"有意义"，不套命名约定回退（避免 NL→LineBreak 等被误判为可跳过）
        referencedTokens = new java.util.HashSet<>();
        for (RuleDef r : rules) {
            if (r.lexer() || r.fragment()) continue;
            for (Alt alt : r.alts()) {
                for (Elem e : alt.elems()) collectTokenRefs(e);
            }
        }

        // 1. 编译词法规则 / fragment（惰性，图无环）
        for (RuleDef r : rules) {
            if (r.lexer() || r.fragment()) compileLexerRule(r);
        }
        // 2. 具名词法 token（fragment 不产 token）
        for (RuleDef r : rules) {
            if (r.lexer() && !r.fragment()) {
                CharMatcher m = lexerCompiled.get(r.name());
                TokenKind kind = resolveKind(r);
                tokenIndex.put(r.name(), tokenRules.size());
                tokenRules.add(new Lexer.TokenRule(r.name(), m, kind, r.mode(), r.modeAction()));
            }
        }
        // 3. 文法规则里的字符串字面量 → 隐式 token（kind=Terminal，名取字面量文本）
        collectImplicitLiterals();

        // 4. 计算左递归标记
        int n = rules.size();
        leftRec = new boolean[n];
        boolean[][] recAlt = new boolean[n][];
        for (RuleDef r : rules) {
            if (!r.lexer() && !r.fragment()) {
                int idx = parserMap.get(r.name());
                boolean[] rec = new boolean[r.alts().size()];
                for (int i = 0; i < rec.length; i++) {
                    rec[i] = altRecursive(r.alts().get(i).elems(), r.name());
                    leftRec[idx] |= rec[i];
                }
                recAlt[idx] = rec;
            }
        }

        // 5. 构造空 RuleBody 壳（供互相引用），再填充
        ruleBodies = new Matchers.RuleBody[n];
        for (RuleDef r : rules) {
            if (!r.lexer() && !r.fragment()) {
                int idx = parserMap.get(r.name());
                ruleBodies[idx] = new Matchers.RuleBody(r.name(), idx);
            }
        }
        for (RuleDef r : rules) {
            if (!r.lexer() && !r.fragment()) {
                fillBody(r, recAlt[parserMap.get(r.name())]);
            }
        }

        int entryIdx = parserMap.get(def.entry());
        Matchers.RuleRef entry = new Matchers.RuleRef(ruleBodies[entryIdx], leftRec[entryIdx]);
        Lexer lexer = new Lexer(tokenRules, opts.lexerLongestMatch());
        return new CompiledGrammar(def.entry(), tokenRules, lexer, ruleBodies, entry);
    }

    private CharMatcher compileLexerRule(RuleDef r) {
        CharMatcher cached = lexerCompiled.get(r.name());
        if (cached != null) return cached;
        // 建序列表：候选 | 序列
        CharMatcher[] alts = new CharMatcher[r.alts().size()];
        for (int i = 0; i < alts.length; i++) {
            List<Elem> elems = r.alts().get(i).elems();
            CharMatcher[] parts = new CharMatcher[elems.size()];
            for (int j = 0; j < parts.length; j++) {
                parts[j] = compileLexerElem(elems.get(j));
            }
            alts[i] = parts.length == 1 ? parts[0] : new CharMatcher.Seq(parts);
        }
        CharMatcher m = alts.length == 1 ? alts[0] : new CharMatcher.Choice(alts);
        lexerCompiled.put(r.name(), m);
        return m;
    }

    private CharMatcher compileLexerElem(Elem e) {
        return switch (e) {
            case ELit lit -> new CharMatcher.Lit(lit.text(), opts.caseInsensitive());
            case EClass c -> new CharMatcher.Class(CharMatcher.parseRanges(c.inner()), c.negated(), opts.caseInsensitive());
            case EAny any -> new CharMatcher.Any();
            case ERef ref -> new CharMatcher.Ref(lexerCompiled.get(ref.name()));
            case EGroup g -> {
                CharMatcher[] alts = new CharMatcher[g.alts().size()];
                for (int i = 0; i < alts.length; i++) {
                    List<Elem> elems = g.alts().get(i).elems();
                    CharMatcher[] parts = new CharMatcher[elems.size()];
                    for (int j = 0; j < parts.length; j++) parts[j] = compileLexerElem(elems.get(j));
                    alts[i] = parts.length == 1 ? parts[0] : new CharMatcher.Seq(parts);
                }
                yield alts.length == 1 ? alts[0] : new CharMatcher.Choice(alts);
            }
            case ERepeat rep -> new CharMatcher.Repeat(compileLexerElem(rep.elem()), rep.min(), rep.max());
            case EAnd a -> new CharMatcher.And(compileLexerElem(a.elem()));
            case ENot n -> new CharMatcher.Not(compileLexerElem(n.elem()));
        };
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
                // 隐式字面量 token：mode 为 null，表示任意模式皆可匹配
                tokenRules.add(new Lexer.TokenRule(e.getKey(),
                        new CharMatcher.Lit(e.getKey(), opts.caseInsensitive()),
                        new TokenKind.Terminal(), null, null));
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

    private void fillBody(RuleDef r, boolean[] rec) {
        int idx = parserMap.get(r.name());
        Matchers.RuleBody body = ruleBodies[idx];
        int n = r.alts().size();
        Matchers.Matcher[] alts = new Matchers.Matcher[n];
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            Alt alt = r.alts().get(i);
            Matchers.Matcher[] parts = new Matchers.Matcher[alt.elems().size()];
            for (int j = 0; j < parts.length; j++) {
                parts[j] = compileParserElem(alt.elems().get(j));
            }
            alts[i] = new Matchers.Seq(parts);
            labels[i] = alt.label();
        }
        body.fill(alts, labels, rec);
    }

    private Matchers.Matcher compileParserElem(Elem e) {
        return switch (e) {
            case ELit lit -> new Matchers.LiteralMatch(lit.text());
            case ERef ref -> {
                if (Character.isUpperCase(ref.name().charAt(0))) {
                    yield new Matchers.TokenMatch(ref.name());
                }
                int t = parserMap.get(ref.name());
                yield new Matchers.RuleRef(ruleBodies[t], leftRec[t]);
            }
            case EGroup g -> {
                Matchers.Matcher[] alts = new Matchers.Matcher[g.alts().size()];
                for (int i = 0; i < alts.length; i++) {
                    Alt alt = g.alts().get(i);
                    Matchers.Matcher[] parts = new Matchers.Matcher[alt.elems().size()];
                    for (int j = 0; j < parts.length; j++) parts[j] = compileParserElem(alt.elems().get(j));
                    alts[i] = new Matchers.Seq(parts);
                }
                yield alts.length == 1 ? alts[0] : new Matchers.Choice(alts);
            }
            case ERepeat rep -> new Matchers.Repeat(compileParserElem(rep.elem()), rep.min(), rep.max());
            case EAnd a -> new Matchers.And(compileParserElem(a.elem()));
            case ENot n -> new Matchers.Not(compileParserElem(n.elem()));
            case EClass c ->
                    throw new com.flora.syntax.peg.GrammarException("文法层不允许字符类（仅词法层可用）");
            case EAny any ->
                    throw new com.flora.syntax.peg.GrammarException("文法层不允许任意字符 '.'（仅词法层可用）");
        };
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
                    return false;
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

    private void collectTokenRefs(Elem e) {
        switch (e) {
            case ERef ref -> {
                if (Character.isUpperCase(ref.name().charAt(0))) referencedTokens.add(ref.name());
            }
            case EGroup g -> g.alts().forEach(a -> a.elems().forEach(x -> collectTokenRefs(x)));
            case ERepeat rep -> collectTokenRefs(rep.elem());
            case EAnd a -> collectTokenRefs(a.elem());
            case ENot n -> collectTokenRefs(n.elem());
            default -> { }
        }
    }

    /** 解析规则上 {@code -> kind(KIND)}；缺省时先按命名约定回退，再兜底 CUSTOM。 */
    private TokenKind resolveKind(RuleDef r) {
        if (r.kindName() != null) return TokenKind.of(r.kindName());
        if (opts.skipRules().contains(r.name())) return new TokenKind.Skip();
        TokenKind byName = conventionKind(r.name());
        if (byName != null) {
            // Trivia 类约定（空白/换行/注释）只对未被文法引用的 token 生效：
            // 被引用的 NL/WS 是"有意义"的 token，不能被自动跳过；非 Trivia 约定（Identifier 等）照常
            if (TokenKind.isTrivia(byName) && referencedTokens.contains(r.name())) {
                return new TokenKind.Custom();
            }
            return byName;
        }
        return new TokenKind.Custom();
    }

    /** 命名约定回退（仅便捷，显式 {@code -> kind} 始终优先）。 */
    private static TokenKind conventionKind(String name) {
        String n = name.toUpperCase();
        return switch (n) {
            case "WS", "WHITESPACE", "SPACE", "SPACES", "BLANK" -> new TokenKind.Whitespace();
            case "NL", "NEWLINE", "EOL", "LINE", "CRLF", "LINE_BREAK" -> new TokenKind.LineBreak();
            case "COMMENT", "COMMENTS", "LINE_COMMENT", "BLOCK_COMMENT" -> new TokenKind.Comment();
            case "ID", "IDENT", "IDENTIFIER" -> new TokenKind.Identifier();
            case "KW", "KEYWORD", "KEYWORDS" -> new TokenKind.Keyword();
            case "INT", "INTEGER", "NUM", "NUMBER", "NUMBER_LITERAL" -> new TokenKind.NumberLiteral();
            case "STRING", "STR", "STRING_LITERAL" -> new TokenKind.StringLiteral();
            case "BOOL", "BOOLEAN", "BOOLEAN_LITERAL" -> new TokenKind.BooleanLiteral();
            case "OP", "OPERATOR", "OPERATORS" -> new TokenKind.Operator();
            case "PUNCT", "PUNCTUATION" -> new TokenKind.Punctuation();
            default -> null;
        };
    }
}
