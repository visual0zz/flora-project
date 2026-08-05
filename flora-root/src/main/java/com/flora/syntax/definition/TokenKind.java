package com.flora.syntax.definition;

/**
 * 引擎内置的通用词法类型继承树（密封，固定词汇表）。
 *
 * <p>语法文件通过 {@code -> kind(KIND)} 从该词汇表选取一个类别标注词法规则；类别由引擎一次性
 * 定义，文法不可自定义新类型。未标注的具名词法规则兜底为 {@link Custom}，文法内联字符串字面量
 * 终端兜底为 {@link Terminal}。简单词法器（{@code com.flora.syntax.Tokenizer}）也复用此词汇表。</p>
 */
public sealed interface TokenKind {

    /** 空边角料（trivia）：保留在 token 列表中，parser 匹配时自动跳过。 */
    sealed interface Trivia extends TokenKind permits Whitespace, LineBreak, Comment {}
    /** 词类。 */
    sealed interface Word extends TokenKind permits Identifier, Keyword {}
    /** 字面量类。 */
    sealed interface Literal extends TokenKind permits NumberLiteral, StringLiteral, BooleanLiteral {}
    /** 符号类。 */
    sealed interface Symbol extends TokenKind permits Operator, Punctuation {}

    record Whitespace() implements Trivia {}
    record LineBreak() implements Trivia {}
    record Comment() implements Trivia {}
    record Identifier() implements Word {}
    record Keyword() implements Word {}
    record NumberLiteral() implements Literal {}
    record StringLiteral() implements Literal {}
    record BooleanLiteral() implements Literal {}
    record Operator() implements Symbol {}
    record Punctuation() implements Symbol {}

    /** 输入结束哨兵，由引擎自动追加，作者不可选。 */
    record Eof() implements TokenKind {}
    /** parser 自动跳过（取代 g4 的 {@code -> skip}），但仍保留在 token 列表中。 */
    record Skip() implements TokenKind {}
    /** 文法内联字符串字面量终端（如 {@code '{'}、{@code 'true'}），非具名词法规则。 */
    record Terminal() implements TokenKind {}
    /** 未标注 {@code -> kind} 的具名词法规则兜底（文法自定义类别）。 */
    record Custom() implements TokenKind {}

    /** 按语法文件中的名字（如 {@code WHITESPACE}、{@code SKIP}）取类别；未知返回 {@code null}。 */
    static TokenKind of(String name) {
        return switch (name) {
            case "WHITESPACE" -> new Whitespace();
            case "LINE_BREAK" -> new LineBreak();
            case "COMMENT" -> new Comment();
            case "IDENTIFIER" -> new Identifier();
            case "KEYWORD" -> new Keyword();
            case "NUMBER_LITERAL" -> new NumberLiteral();
            case "STRING_LITERAL" -> new StringLiteral();
            case "BOOLEAN_LITERAL" -> new BooleanLiteral();
            case "OPERATOR" -> new Operator();
            case "PUNCTUATION" -> new Punctuation();
            case "EOF" -> new Eof();
            case "SKIP" -> new Skip();
            case "TERMINAL" -> new Terminal();
            case "CUSTOM" -> new Custom();
            default -> null;
        };
    }

    /** 是否为 trivia 组别（保留 + parser 自动跳过）。 */
    static boolean isTrivia(TokenKind kind) {
        return kind instanceof Trivia;
    }

    /** 是否被 parser 自动跳过（trivia 或 SKIP）。 */
    static boolean autoSkipped(TokenKind kind) {
        return kind instanceof Trivia || kind instanceof Skip;
    }
}
