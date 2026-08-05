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
    WHITESPACE(Category.TRIVIA), LINE_BREAK(Category.TRIVIA), COMMENT(Category.TRIVIA),
    /** 词类。 */
    IDENTIFIER(Category.WORD), KEYWORD(Category.WORD),
    /** 字面量类。 */
    NUMBER_LITERAL(Category.LITERAL), STRING_LITERAL(Category.LITERAL), BOOLEAN_LITERAL(Category.LITERAL),
    /** 符号类。 */
    OPERATOR(Category.SYMBOL), PUNCTUATION(Category.SYMBOL),

    /** 输入结束哨兵，由引擎自动追加，作者不可选。 */
    EOF(Category.NONE),
    /** parser 自动跳过（取代 g4 的 {@code -> skip}），但仍保留在 token 列表中。 */
    SKIP(Category.NONE),
    /** 文法内联字符串字面量终端（如 {@code '{'}、{@code 'true'}），非具名词法规则。 */
    TERMINAL(Category.NONE),
    /** 未标注 {@code -> kind} 的具名词法规则兜底（文法自定义类别）。 */
    CUSTOM(Category.NONE);

    /** 词法类别分组：按用途归组的语义集合。 */
    public enum Category {
        /** 空边角料：保留在 token 列表中，parser 自动跳过。 */
        TRIVIA,
        /** 词类。 */
        WORD,
        /** 字面量类。 */
        LITERAL,
        /** 符号类。 */
        SYMBOL,
        /** 不归入上述任何组别（EOF / SKIP / TERMINAL / CUSTOM）。 */
        NONE
    }

    private final Category category;

    TokenKind(Category category) {
        this.category = category;
    }

    /** 该类别所属的分组。 */
    public Category category() {
        return category;
    }

    /** 是否被 parser 自动跳过（trivia 或 SKIP）。 */
    public boolean autoSkip() {
        return category == Category.TRIVIA || this == SKIP;
    }

    /** 按枚举名（即语法文件 {@code -> kind(...)} 中的名字）取类别；未知返回 {@code null}。 */
    public static TokenKind of(String name) {
        for (TokenKind k : values()) {
            if (k.name().equals(name)) return k;
        }
        return null;
    }
}
