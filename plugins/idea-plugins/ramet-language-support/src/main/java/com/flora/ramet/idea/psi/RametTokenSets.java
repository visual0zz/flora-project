package com.flora.ramet.idea.psi;

import com.flora.ramet.idea.lexer.RametTokenTypes;
import com.intellij.psi.tree.TokenSet;

/**
 * Ramet 模板语言的 Token 分类集合。
 */
public final class RametTokenSets {

    private RametTokenSets() {
    }

    /** 指令关键字（if, for, list, include, macro, else, elseif, continue, break, meta）。 */
    public static final TokenSet DIRECTIVE_KEYWORDS = TokenSet.create(
            RametTokenTypes.IF,
            RametTokenTypes.FOR,
            RametTokenTypes.INCLUDE,
            RametTokenTypes.MACRO,
            RametTokenTypes.ELSE,
            RametTokenTypes.ELSEIF,
            RametTokenTypes.CONTINUE,
            RametTokenTypes.BREAK,
            RametTokenTypes.META
    );

    /** 所有指令相关的 token（含标签符号）。 */
    public static final TokenSet DIRECTIVES = TokenSet.create(
            RametTokenTypes.IF,
            RametTokenTypes.FOR,
            RametTokenTypes.INCLUDE,
            RametTokenTypes.MACRO,
            RametTokenTypes.ELSE,
            RametTokenTypes.ELSEIF,
            RametTokenTypes.CONTINUE,
            RametTokenTypes.BREAK,
            RametTokenTypes.META,
            RametTokenTypes.END
    );

    /** 注释。 */
    public static final TokenSet COMMENTS = TokenSet.create(
            RametTokenTypes.COMMENT
    );

    /** 字符串字面量（指令/宏调用中的 "..."）。 */
    public static final TokenSet STRINGS = TokenSet.create(
            RametTokenTypes.STRING
    );

    /** 字面量（数字、布尔）。 */
    public static final TokenSet LITERALS = TokenSet.create(
            RametTokenTypes.NUMBER,
            RametTokenTypes.BOOLEAN
    );

    /** 运算符/引用。 */
    public static final TokenSet OPERATORS = TokenSet.create(
            RametTokenTypes.OPERATOR,
            RametTokenTypes.DOT
    );

    /** 变量插值。 */
    public static final TokenSet VAR_INTERPOLATIONS = TokenSet.create(
            RametTokenTypes.VAR
    );

    /** 宏调用。 */
    public static final TokenSet MACRO_CALLS = TokenSet.create(
            RametTokenTypes.MACRO_CALL
    );

    /** 需要被语法高亮着色的所有 token 类型（非 PASSIVE 和 NEWLINE）。 */
    public static final TokenSet HIGHLIGHT_TOKENS = TokenSet.create(
            RametTokenTypes.VAR,
            RametTokenTypes.COMMENT,
            RametTokenTypes.IF,
            RametTokenTypes.ELSE,
            RametTokenTypes.ELSEIF,
            RametTokenTypes.END,
            RametTokenTypes.FOR,
            RametTokenTypes.INCLUDE,
            RametTokenTypes.MACRO,
            RametTokenTypes.MACRO_CALL,
            RametTokenTypes.CONTINUE,
            RametTokenTypes.BREAK,
            RametTokenTypes.META
    );
}
