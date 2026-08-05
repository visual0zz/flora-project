package com.flora.syntax.peg;

/**
 * 编译选项（{@code Grammar.compile(definition, options)}）。
 *
 * <ul>
 *   <li>{@code caseInsensitive}：字面量与字符类大小写不敏感。</li>
 *   <li>{@code lexerLongestMatch}：分词最长匹配（默认开）；关则为 PEG 有序（首匹配）。</li>
 * </ul>
 */
public final class GrammarOptions {
    private boolean caseInsensitive;
    private boolean lexerLongestMatch = true;

    public GrammarOptions caseInsensitive(boolean value) {
        this.caseInsensitive = value;
        return this;
    }

    public GrammarOptions lexerLongestMatch(boolean value) {
        this.lexerLongestMatch = value;
        return this;
    }

    public boolean caseInsensitive() { return caseInsensitive; }
    public boolean lexerLongestMatch() { return lexerLongestMatch; }
}
