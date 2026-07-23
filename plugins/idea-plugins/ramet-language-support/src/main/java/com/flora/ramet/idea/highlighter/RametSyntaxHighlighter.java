package com.flora.ramet.idea.highlighter;

import com.flora.ramet.idea.lexer.RametLexer;
import com.flora.ramet.idea.lexer.RametTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.ui.JBColor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Ramet 模板语言的语法高亮器——提供基础着色层。
 *
 * <p>粗粒度 token 颜色映射。细粒度高亮由 {@link RametAnnotator} 补充。
 *
 * <p>颜色策略：Ramet 模板元素用鲜艳高饱和度语义色，
 * TEXT（非模板）内容用暗淡低饱和度色。
 */
public class RametSyntaxHighlighter extends SyntaxHighlighterBase {

    private static final Logger LOG = Logger.getInstance(RametSyntaxHighlighter.class);

    // ===== Ramet 模板元素 —— 鲜艳高饱和度 =====

    /** 指令关键字（if, for, list, include, macro, else, continue, break, meta）。 */
    public static final TextAttributesKey RAMET_KEYWORD =
            TextAttributesKey.createTextAttributesKey("RAMET_KEYWORD");
    /** 字符串 */
    public static final TextAttributesKey RAMET_STRING =
            TextAttributesKey.createTextAttributesKey("RAMET_STRING");
    /** 数字 */
    public static final TextAttributesKey RAMET_NUMBER =
            TextAttributesKey.createTextAttributesKey("RAMET_NUMBER");
    /** 函数名（concat, javaPackageToPath 等） */
    public static final TextAttributesKey RAMET_FUNCTION =
            TextAttributesKey.createTextAttributesKey("RAMET_FUNCTION");
    /** 变量引用（${...} 中的引用标识符）——粉红色，与定义处的橙黄色区分 */
    public static final TextAttributesKey RAMET_VARIABLE =
            TextAttributesKey.createTextAttributesKey("RAMET_VARIABLE");
    /** Meta 注解（@Param, @Cartesian, @Path, @Config） */
    public static final TextAttributesKey RAMET_ANNOTATION =
            TextAttributesKey.createTextAttributesKey("RAMET_ANNOTATION");
    /** 运算符/内置函数（greaterThan, and, not 等） */
    public static final TextAttributesKey RAMET_OPERATOR =
            TextAttributesKey.createTextAttributesKey("RAMET_OPERATOR");
    /** Lson key（package:, name: 等），与 KEYWORD 和 STRING 区分开 */
    public static final TextAttributesKey RAMET_LSON_KEY =
            TextAttributesKey.createTextAttributesKey("RAMET_LSON_KEY");
    /** 尖括号/括号（表达式块内的 (){}[] 也用此键，统一着白色以提升可读性） */
    public static final TextAttributesKey RAMET_BRACKET =
            TextAttributesKey.createTextAttributesKey("RAMET_BRACKET");
    /** 注释：内置指令注释和被动块内多语言注释。 */
    public static final TextAttributesKey RAMET_COMMENT =
            TextAttributesKey.createTextAttributesKey("RAMET_COMMENT");

    // ===== TEXT（非模板区域）—— 暗淡低饱和度 =====

    /** TEXT 块默认文本色 */
    public static final TextAttributesKey TEXT_DEFAULT =
            TextAttributesKey.createTextAttributesKey("TEXT_DEFAULT");
    /** TEXT 块内字符串 */
    public static final TextAttributesKey TEXT_STRING =
            TextAttributesKey.createTextAttributesKey("TEXT_STRING");
    /** TEXT 块内注释 */
    public static final TextAttributesKey TEXT_COMMENT =
            TextAttributesKey.createTextAttributesKey("TEXT_COMMENT");
    /** TEXT 块内数字 */
    public static final TextAttributesKey TEXT_NUMBER =
            TextAttributesKey.createTextAttributesKey("TEXT_NUMBER");
    /** TEXT 块内注解（@Override 等）和函数调用——统一用暗黄色 */
    public static final TextAttributesKey TEXT_ANNOTATION =
            TextAttributesKey.createTextAttributesKey("TEXT_ANNOTATION");
    /** TEXT 块内函数调用 */
    public static final TextAttributesKey TEXT_FUNCTION =
            TextAttributesKey.createTextAttributesKey("TEXT_FUNCTION");
    /** TEXT 块内运算符（==, !=, +=, && 等） */
    public static final TextAttributesKey TEXT_OPERATOR =
            TextAttributesKey.createTextAttributesKey("TEXT_OPERATOR");
    /** TEXT 块内尖括号（< > 泛型或比较） */
    public static final TextAttributesKey TEXT_ANGLE =
            TextAttributesKey.createTextAttributesKey("TEXT_ANGLE");

    // ===== Token 到颜色映射 =====

    private static final Map<IElementType, TextAttributesKey> TOKEN_COLORS = new HashMap<>();

    static {
        // 模板指令基于 KEYWORD，Annotator 做细化
        TOKEN_COLORS.put(RametTokenTypes.IF, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.FOR, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.INCLUDE, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.MACRO, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.ELSE, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.ELSEIF, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.END, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.KEYWORD, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.OPERATOR, RAMET_OPERATOR);
        TOKEN_COLORS.put(RametTokenTypes.COMMENT, RAMET_COMMENT);
        TOKEN_COLORS.put(RametTokenTypes.CONTINUE, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.BREAK, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.META, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.ANNOTATION, RAMET_ANNOTATION);
        TOKEN_COLORS.put(RametTokenTypes.STRING, RAMET_STRING);
        TOKEN_COLORS.put(RametTokenTypes.NUMBER, RAMET_NUMBER);
        TOKEN_COLORS.put(RametTokenTypes.BOOLEAN, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.IDENTIFIER, DefaultLanguageHighlighterColors.IDENTIFIER);
        TOKEN_COLORS.put(RametTokenTypes.LT, RAMET_BRACKET);
        TOKEN_COLORS.put(RametTokenTypes.GT, RAMET_BRACKET);
        TOKEN_COLORS.put(RametTokenTypes.HASH, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.DOLLAR, RAMET_KEYWORD);
        TOKEN_COLORS.put(RametTokenTypes.SLASH, RAMET_OPERATOR);
        TOKEN_COLORS.put(RametTokenTypes.DOT, DefaultLanguageHighlighterColors.DOT);
        TOKEN_COLORS.put(RametTokenTypes.PAREN_L, DefaultLanguageHighlighterColors.PARENTHESES);
        TOKEN_COLORS.put(RametTokenTypes.PAREN_R, DefaultLanguageHighlighterColors.PARENTHESES);
        TOKEN_COLORS.put(RametTokenTypes.LBRACE, RAMET_BRACKET);
        TOKEN_COLORS.put(RametTokenTypes.RBRACE, RAMET_BRACKET);
        TOKEN_COLORS.put(RametTokenTypes.COMMA, DefaultLanguageHighlighterColors.COMMA);
        TOKEN_COLORS.put(RametTokenTypes.COLON, RAMET_OPERATOR);
        TOKEN_COLORS.put(RametTokenTypes.RANGE, RAMET_OPERATOR);

        // TEXT 默认暗淡
        TOKEN_COLORS.put(RametTokenTypes.NEWLINE, TEXT_DEFAULT);
        TOKEN_COLORS.put(RametTokenTypes.PASSIVE, TEXT_DEFAULT);
        TOKEN_COLORS.put(RametTokenTypes.WHITESPACE, TEXT_DEFAULT);
        TOKEN_COLORS.put(RametTokenTypes.MACRO_CALL, RAMET_FUNCTION);
        TOKEN_COLORS.put(RametTokenTypes.VAR, RAMET_STRING);
    }

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        LOG.info("getHighlightingLexer() called");
        return new RametLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        TextAttributesKey key = TOKEN_COLORS.get(tokenType);
        if (key != null) {
            return new TextAttributesKey[]{key};
        }
        if (tokenType != null) {
            LOG.warn("getTokenHighlights() unknown token type: " + tokenType);
        }
        return TextAttributesKey.EMPTY_ARRAY;
    }
}
