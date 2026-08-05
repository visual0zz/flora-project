package com.flora.syntax.peg;

/**
 * 编译选项（{@code Grammar.compile(definition, options)}）。
 *
 * <ul>
 *   <li>{@code caseInsensitive}：字面量与字符类大小写不敏感。</li>
 *   <li>{@code lexerLongestMatch}：分词最长匹配（默认开）；关则为 PEG 有序（首匹配）。</li>
 *   <li>{@code autoSkip}：parser 自动跳过 trivia 与 {@code kind(SKIP)} 的 token（默认开）；
 *       关闭后这些 token 保留在 parser 流中，文法规则需显式引用它们。</li>
 * </ul>
 */
public final class GrammarOptions {
    private boolean caseInsensitive;
    private boolean lexerLongestMatch = true;
    private boolean autoSkip = true;

    public GrammarOptions caseInsensitive(boolean value) {
        this.caseInsensitive = value;
        return this;
    }

    public GrammarOptions lexerLongestMatch(boolean value) {
        this.lexerLongestMatch = value;
        return this;
    }

    public GrammarOptions autoSkip(boolean value) {
        this.autoSkip = value;
        return this;
    }

    public boolean caseInsensitive() { return caseInsensitive; }
    public boolean lexerLongestMatch() { return lexerLongestMatch; }
    public boolean autoSkip() { return autoSkip; }
}
