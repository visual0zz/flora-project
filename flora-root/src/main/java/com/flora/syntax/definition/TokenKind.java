package com.flora.syntax.definition;

/**
 * 引擎内置的通用词法类别（固定词汇表）。
 *
 * <p>语法文件通过 {@code -> kind(KIND)} 从该词汇表选取一个类别标注词法规则；类别由引擎一次性
 * 定义，文法不可自定义新类型。未标注的具名词法规则兜底为 {@link #CUSTOM}，文法内联字符串字面量
 * 终端兜底为 {@link #TERMINAL}。简单词法器（{@code com.flora.syntax.Tokenizer}）也复用此词汇表。</p>
 */
public enum TokenKind {

    /** 空边角料（trivia）：保留在 token 列表中，parser 匹配时自动跳过。 */
    WHITESPACE, LINE_BREAK, COMMENT,
    /** 词类。 */
    IDENTIFIER, KEYWORD,
    /** 字面量类。 */
    NUMBER_LITERAL, STRING_LITERAL, BOOLEAN_LITERAL,
    /** 符号类。 */
    OPERATOR, PUNCTUATION,

    /** 输入结束哨兵，由引擎自动追加，作者不可选。 */
    EOF,
    /** parser 自动跳过（取代 g4 的 {@code -> skip}），但仍保留在 token 列表中。 */
    SKIP,
    /** 文法内联字符串字面量终端（如 {@code '{'}、{@code 'true'}），非具名词法规则。 */
    TERMINAL,
    /** 未标注 {@code -> kind} 的具名词法规则兜底（文法自定义类别）。 */
    CUSTOM;

    /** 按语法文件中的名字（如 {@code WHITESPACE}、{@code SKIP}）取类别；未知返回 {@code null}。 */
    public static TokenKind of(String name) {
        return switch (name) {
            case "WHITESPACE" -> WHITESPACE;
            case "LINE_BREAK" -> LINE_BREAK;
            case "COMMENT" -> COMMENT;
            case "IDENTIFIER" -> IDENTIFIER;
            case "KEYWORD" -> KEYWORD;
            case "NUMBER_LITERAL" -> NUMBER_LITERAL;
            case "STRING_LITERAL" -> STRING_LITERAL;
            case "BOOLEAN_LITERAL" -> BOOLEAN_LITERAL;
            case "OPERATOR" -> OPERATOR;
            case "PUNCTUATION" -> PUNCTUATION;
            case "EOF" -> EOF;
            case "SKIP" -> SKIP;
            case "TERMINAL" -> TERMINAL;
            case "CUSTOM" -> CUSTOM;
            default -> null;
        };
    }

    /** 是否为 trivia 组别（保留 + parser 自动跳过）。 */
    public boolean isTrivia() {
        return this == WHITESPACE || this == LINE_BREAK || this == COMMENT;
    }

    /** 是否被 parser 自动跳过（trivia 或 SKIP）。 */
    public boolean autoSkipped() {
        return isTrivia() || this == SKIP;
    }
}
