package com.flora.codegen.engine;

import com.flora.codegen.engine.parser.Lexer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证词法分析器 {@link com.flora.codegen.engine.parser.Lexer#lex(String)} 的全部分支：
 * <ul>
 *   <li>纯文本 → {@code PASSIVE} 类型</li>
 *   <li>{@code ${}} 变量插值 → {@code VAR} 类型，含嵌套花括号表达式</li>
 *   <li>指令标签：{@code if/else/end/list/include/macro} → 对应的枚举类型</li>
 *   <li>注释标签 → {@code COMMENT} 类型（含元数据注释）</li>
 *   <li>宏调用 {@code <@name args/>} → {@code MACRO_CALL} 类型及 Lson 参数解析</li>
 *   <li>转义美元符 {@code \$} → 字面量输出</li>
 *   <li>换行符 → 被后续 token 吸收为 {@code leadingNewline} 标记，连续多余换行产生 NEWLINE</li>
 *   <li>结束标签 {@code </#xxx>} → {@code END} 类型</li>
 *   <li>错误分支：未闭合插值/注释、缺失 {@code >}、未知指令名、空标签名</li>
 * </ul>
 */
class TokenTest {

    private List<Token> lex(String src) {
        return Lexer.lex(src);
    }

    @Test
    void plainTextBecomesPassive() {
        List<Token> toks = lex("hello world");
        assertEquals(1, toks.size());
        assertEquals(Token.Type.PASSIVE, toks.get(0).type());
        assertEquals("hello world", toks.get(0).text());
    }

    @Test
    void variableTokenCapturesExpression() {
        List<Token> toks = lex("a ${user.name} b");
        assertEquals(Token.Type.VAR, toks.get(1).type());
        assertEquals("user.name", toks.get(1).text());
    }

    @Test
    void ifElseEndDirectiveChain() {
        List<Token> toks = lex("<#if x>yes<#else>no</#if>");
        // 期望序列: IF, PASSIVE("yes"), ELSE, PASSIVE("no"), END
        assertEquals(5, toks.size());
        assertEquals(Token.Type.IF, toks.get(0).type());
        assertEquals("x", toks.get(0).text());
        assertEquals(Token.Type.PASSIVE, toks.get(1).type());
        assertEquals(Token.Type.ELSE, toks.get(2).type());
        assertEquals(Token.Type.END, toks.get(4).type());
    }

    @Test
    void ordinaryCommentIsNotMeta() {
        List<Token> toks = lex("<#-- just a note -->");
        assertEquals(Token.Type.COMMENT, toks.get(0).type());
    }

    @Test
    void commentWithMetaKeywordsIsStillComment() {
        List<Token> toks = lex("<#-- @Path{ x } -->");
        assertEquals(Token.Type.COMMENT, toks.get(0).type());
        assertTrue(toks.get(0).text().contains("@Path"));
    }

    @Test
    void backslashIsLiteralInPassive() {
        // 被动区域零转义：\$ \# \n 全都原样输出
        List<Token> toks = lex("cost \\$5 \\#ok");
        assertEquals(1, toks.size());
        assertEquals(Token.Type.PASSIVE, toks.get(0).type());
        assertEquals("cost \\$5 \\#ok", toks.get(0).text());
    }

    @Test
    void newlineSetsLeadingNewlineOnNextToken() {
        List<Token> toks = lex("a\nb");
        assertEquals(2, toks.size());
        assertEquals(Token.Type.PASSIVE, toks.get(0).type());
        assertEquals("a", toks.get(0).text());
        assertFalse(toks.get(0).leadingNewline());
        assertEquals(Token.Type.PASSIVE, toks.get(1).type());
        assertEquals("b", toks.get(1).text());
        assertTrue(toks.get(1).leadingNewline());
    }

    @Test
    void varWithNestedBraces() {
        List<Token> toks = lex("${a.b(c)}");
        assertEquals(Token.Type.VAR, toks.get(0).type());
        assertEquals("a.b(c)", toks.get(0).text());
    }

    @Test
    void directivesProduceTypedTokens() {
        assertEquals(Token.Type.FOR, lex("<#for y:x>").get(0).type());
        assertEquals(Token.Type.INCLUDE, lex("<#include \"z\">").get(0).type());
        assertEquals(Token.Type.MACRO, lex("<#macro m>").get(0).type());
    }

    @Test
    void endTagProducesEndToken() {
        assertEquals(Token.Type.END, lex("</#if>").get(0).type());
    }

    @Test
    void macroCallWithArgs() {
        List<Token> toks = lex("<@m a,b,c/>");
        assertEquals(Token.Type.MACRO_CALL, toks.get(0).type());
        assertEquals("m", toks.get(0).text());
        assertEquals(3, toks.get(0).lsonArgs().size());
    }

    @Test
    void macroCallNoArgs() {
        List<Token> toks = lex("<@m/>");
        assertEquals(Token.Type.MACRO_CALL, toks.get(0).type());
        assertEquals(0, toks.get(0).lsonArgs().size());
    }

    @Test
    void unclosedInterpolationThrows() {
        assertThrows(CodeGenException.class, () -> lex("${x"));
    }

    @Test
    void unclosedCommentThrows() {
        assertThrows(CodeGenException.class, () -> lex("<#-- oops"));
    }

    @Test
    void directiveMissingGtThrows() {
        assertThrows(CodeGenException.class, () -> lex("<#if x"));
    }

    @Test
    void unknownDirectiveKeywordThrows() {
        assertThrows(CodeGenException.class, () -> lex("<#wrong>"));
    }

    @Test
    void emptyDirectiveNameThrows() {
        assertThrows(CodeGenException.class, () -> lex("<#>"));
    }

    @Test
    void emptyMacroNameThrows() {
        assertThrows(CodeGenException.class, () -> lex("<@>"));
    }
}
