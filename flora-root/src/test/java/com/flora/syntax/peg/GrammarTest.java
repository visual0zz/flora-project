package com.flora.syntax.peg;

import com.flora.syntax.common.definition.Token;
import com.flora.syntax.common.definition.TokenKind;
import com.flora.syntax.common.exceptions.ParseException;
import com.flora.syntax.common.exceptions.SyntaxException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrammarTest {

    private static final String JSON = """
            @start value;
            value  : object | array | String | Number | 'true' # bool | 'false' # bool | 'null' # nullv ;
            object : '{' (String ':' value (',' String ':' value)*)? '}' ;
            array  : '[' (value (',' value)*)? ']' ;
            fragment DIGIT : [0-9] ;
            String : '"' ~["]* '"' -> kind(STRING_LITERAL) ;
            Number : DIGIT+ ('.' DIGIT+)? -> kind(NUMBER_LITERAL) ;
            WS     : [ \\t\\n\\r]+ -> kind(SKIP) ;
            """;

    private static final String CALC = """
            @start expr;
            expr  : term (('+' | '-') term)* ;
            term  : factor (('*' | '/') factor)* ;
            factor: Number | '(' expr ')' | '-' factor ;
            Number: [0-9]+ -> kind(NUMBER_LITERAL) ;
            WS    : [ \\t\\n]+ -> kind(SKIP) ;
            """;

    private static final String PAREN = """
            @start s;
            s : '(' s ')' | N ;
            N : [0-9]+ ;
            WS : [ \\t\\n]+ -> kind(SKIP) ;
            """;

    @Test
    void jsonParsesToTreeAndTokens() {
        Grammar g = Grammar.compile(JSON);
        ParseOutput out = g.parse("{ \"a\" : [ 1, 2.5 ] , \"b\" : null }");
        assertTrue(out.success());
        ParseTree t = out.tree();
        assertEquals("value", t.name());
        // 树不包含被跳过的空白（WS→SKIP 由 parser 自动跳过）
        assertEquals("{\"a\":[1,2.5],\"b\":null}", t.text());
        // tokens() 返回全部 token（含 SKIP 的空白），供链式分别取出
        List<Token> toks = out.tokens();
        assertTrue(toks.stream().anyMatch(tk -> tk.kind() == TokenKind.SKIP));
        assertTrue(toks.stream().anyMatch(tk -> tk.kind() == TokenKind.TERMINAL));
        assertTrue(toks.stream().anyMatch(tk -> tk.kind() == TokenKind.STRING_LITERAL));
        assertTrue(toks.stream().anyMatch(tk -> tk.kind() == TokenKind.NUMBER_LITERAL));
    }

    @Test
    void chainTokensAndTree() {
        Grammar g = Grammar.compile(JSON);
        assertEquals("{\"a\":[1,2.5],\"b\":null}",
                Grammar.compile(JSON).parse("{\"a\":[1,2.5],\"b\":null}").tree().text());
        List<Token> toks = Grammar.compile(JSON).parse("{\"a\":[1,2.5],\"b\":null}").tokens();
        assertEquals(TokenKind.EOF, toks.get(toks.size() - 1).kind());
    }

    @Test
    void labelNamesAlternative() {
        ParseTree t = Grammar.compile(JSON).parse("null").tree();
        assertEquals("nullv", t.name());
        ParseTree t2 = Grammar.compile(JSON).parse("true").tree();
        assertEquals("bool", t2.name());
    }

    @Test
    void calculatorWithPrecedence() {
        Grammar g = Grammar.compile(CALC);
        ParseTree t = g.parse("1+2*3").tree();
        assertEquals("expr", t.name());
        assertEquals("1+2*3", t.text());
        ParseTree t2 = g.parse("-1+(2)").tree();
        assertEquals("expr", t2.name());
    }

    @Test
    void nestedParens() {
        Grammar g = Grammar.compile(PAREN);
        assertEquals("s", g.parse("((1))").tree().name());
        assertEquals("((1))", g.parse("((1))").tree().text());
        assertTrue(g.tryParse("(1)").success());
    }

    @Test
    void parseFailureReportsPosition() {
        Grammar g = Grammar.compile(JSON);
        ParseException ex = assertThrows(ParseException.class, () -> g.parse("{\"a\":}"));
        assertTrue(ex.line() >= 1);
        assertTrue(ex.offset() >= 0);
        assertTrue(!ex.expected().isEmpty());
    }

    @Test
    void tryParseDoesNotThrow() {
        Grammar g = Grammar.compile(JSON);
        ParseOutput bad = g.tryParse("{\"a\":}");
        assertTrue(!bad.success());
        assertTrue(bad.error() != null);
        // 失败时 tokens() 仍给出词法产物
        assertTrue(!bad.tokens().isEmpty());
    }

    @Test
    void explicitKindAndCustom() {
        // kind 只能显式 -> kind(...) 指定；未标注一律 CUSTOM（无命名约定猜测）
        Grammar g = Grammar.compile("""
                @start r;
                r : ID MYSTUFF ;
                ID : [a-z]+ -> kind(IDENTIFIER) ;
                MYSTUFF : [0-9]+ ;
                WS : [ \\t]+ -> kind(SKIP) ;
                """);
        List<Token> toks = g.parse("abc 123").tokens();
        assertEquals(TokenKind.IDENTIFIER, toks.get(0).kind()); // 显式标注
        assertEquals(TokenKind.SKIP, toks.get(1).kind());        // 显式 kind(SKIP) 保留在列表
        assertEquals(TokenKind.CUSTOM, toks.get(2).kind());      // 未标注 → CUSTOM
    }

    @Test
    void lexerEmptyMatchRejected() {
        assertThrows(SyntaxException.class, () -> Grammar.compile("""
                @start r;
                r : A ;
                A : [a-z]* ;
                """));
    }

    @Test
    void leftRecursionSupportedBySeedGrowing() {
        // Warth 种子生长：直接左递归文法可解析，且天然左结合
        Grammar g = Grammar.compile("""
                @start expr;
                expr : expr '+' term # Add | expr '-' term # Sub | term ;
                term : Number ;
                Number: [0-9]+ ;
                WS    : [ \\t\\n]+ -> kind(SKIP) ;
                """);
        ParseTree t = g.parse("1+2-3").tree();
        assertEquals("1+2-3", t.text());
        // 左结合：根为最外层 '-'(Sub)，其子 expr 是 '(1+2)'(Add)
        assertEquals("Sub", t.name());
        assertTrue(t.children().stream().anyMatch(c -> c.name().equals("Add")));
        assertTrue(t.children().stream().anyMatch(c -> c.name().equals("-")));
    }

    @Test
    void indirectLeftRecursionRejected() {
        assertThrows(SyntaxException.class, () -> Grammar.compile("""
                @start a;
                a : b 'x' | 'a' ;
                b : a 'y' ;
                """));
    }

    @Test
    void lexerModes() {
        // 词法模式：字符串内（IN_STRING）引号语义不同于默认模式
        Grammar g = Grammar.compile("""
                @start s;
                s : StrChar* ;
                StrOpen  : '"' -> pushMode(IN_STRING), kind(SKIP) ;
                mode IN_STRING;
                StrClose : '"' -> popMode, kind(SKIP) ;
                StrChar  : ~["] | '\\\\' . ;
                """);
        ParseOutput out = g.parse("\"hello\"");
        assertTrue(out.success());
        assertEquals("hello", out.tree().text());
        // 引号被 SKIP（保留在 tokens），字符串内容为 StrChar token
        assertTrue(out.tokens().stream().anyMatch(tk -> tk.kind() == TokenKind.SKIP));
        assertTrue(out.tokens().stream().anyMatch(tk -> tk.typeName().equals("StrChar")));
        // 空字符串 ""：开/闭引号均被 SKIP 并切换模式，内容为空也能匹配
        assertTrue(g.tryParse("\"\"").success());
        // 模式切换真实生效：输入不含引号时，IN_STRING 模式的 StrChar 在默认模式下不可用 → 无法识别
        assertTrue(!g.tryParse("abc").success());
    }

    @Test
    void lexerCharClassAndPredicate() {
        Grammar g = Grammar.compile("""
                @start s;
                s : WORD ;
                WORD : [A-Za-z]+ ;
                NL : '\\n' -> kind(LINE_BREAK) ;
                WS : [ \\t]+ -> kind(WHITESPACE) ;
                """);
        ParseTree t = g.parse("abc").tree();
        assertEquals("s", t.name());
    }
}
