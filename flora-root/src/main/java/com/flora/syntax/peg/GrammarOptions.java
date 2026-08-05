package com.flora.syntax.peg;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 编译选项（{@code Grammar.compile(definition, options)}）。
 *
 * <ul>
 *   <li>{@code caseInsensitive}：字面量与字符类大小写不敏感。</li>
 *   <li>{@code skipRule(name)}：等价于 {@code -> kind(SKIP)}（将该规则标为丢弃）。</li>
 *   <li>{@code lexerLongestMatch}：分词最长匹配（默认开）；关则为 PEG 有序（首匹配）。</li>
 * </ul>
 */
public final class GrammarOptions {
    private boolean caseInsensitive;
    private final Set<String> skipRules = new LinkedHashSet<>();
    private boolean lexerLongestMatch = true;

    public GrammarOptions caseInsensitive(boolean value) {
        this.caseInsensitive = value;
        return this;
    }

    public GrammarOptions skipRule(String name) {
        this.skipRules.add(name);
        return this;
    }

    public GrammarOptions lexerLongestMatch(boolean value) {
        this.lexerLongestMatch = value;
        return this;
    }

    public boolean caseInsensitive() { return caseInsensitive; }
    public Set<String> skipRules() { return Set.copyOf(skipRules); }
    public boolean lexerLongestMatch() { return lexerLongestMatch; }
}
