package com.flora.syntax.peg;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语法引擎特性覆盖测试：词法边界、文法组合子、选项、公共 API。
 * 作为后续"直接解释 Elem AST"重构的行为契约。
 */
class GrammarFeatureTest {

    private static final String PAREN = """
            @start s;
            s : '(' s ')' | N ;
            N : [0-9]+ ;
            """;

    @Test
    void charClassRanges() {
        Grammar g = Grammar.compile("""
                @start r;
                r : HEX ;
                HEX : [a-f0-9]+ ;
                """);
        assertEquals("1af9", g.parse("1af9").tree().text());
    }

    @Test
    void literalEscapes() {
        Grammar g = Grammar.compile("""
                @start r;
                r : ID NL Q ;
                ID : [a-z]+ ;
                NL : '\\n' ;
                Q  : '\\'' ;
                """);
        ParseTree t = g.parse("abc\n'").tree();
        assertEquals("abc\n'", t.text());
    }

    @Test
    void negatedClassAndDot() {
        Grammar g = Grammar.compile("""
                @start r;
                r : STR DOT+ ;
                STR : '"' ~["]* '"' ;
                DOT : . ;
                """);
        ParseTree t = g.parse("\"hi there\"xyz").tree();
        assertEquals("\"hi there\"xyz", t.text());
    }

    @Test
    void longestMatchTieBreakByDeclaration() {
        Grammar g = Grammar.compile("""
                @start r;
                r : KW | ID ;
                KW : 'if' ;
                ID : [a-z]+ ;
                """);
        // "if"：两个规则都匹配 2 字符，平局按声明顺序 → KW
        assertEquals("KW", g.parse("if").tokens().get(0).typeName());
        // "iff"：ID 最长匹配 3 字符 → ID
        assertEquals("ID", g.parse("iff").tokens().get(0).typeName());
    }

    @Test
    void lexerOrderedModeFirstMatch() {
        String def = """
                @start r;
                r : KW ID ;
                KW : 'if' ;
                ID : [a-z]+ ;
                """;
        // 默认最长匹配："iff" 整体为 ID → r : KW ID 失败
        assertFalse(Grammar.compile(def).tryParse("iff").success());
        // 有序模式（首匹配）："if" 先被 KW 吃掉，剩 "f" 为 ID → 成功
        Grammar ordered = Grammar.compile(def, new GrammarOptions().lexerLongestMatch(false));
        assertTrue(ordered.tryParse("iff").success());
    }

    @Test
    void caseInsensitiveOption() {
        String def = """
                @start r;
                r : SELECT ;
                SELECT : 'select' ;
                """;
        assertFalse(Grammar.compile(def).tryParse("SELECT").success());
        Grammar ci = Grammar.compile(def, new GrammarOptions().caseInsensitive(true));
        assertTrue(ci.tryParse("SELECT").success());
    }

    @Test
    void skipRuleOption() {
        String def = """
                @start r;
                r : ID ID ;
                ID : [a-z]+ ;
                GAP : [ \\t]+ ;
                """;
        // 未标 skip：GAP 无约定无标注 → Custom，parser 不跳过 → 第二个 ID 前遇到 GAP → 失败
        assertFalse(Grammar.compile(def).tryParse("abc  def").success());
        // skipRule("GAP")：等价于 kind(SKIP)，被自动跳过 → 成功
        Grammar g = Grammar.compile(def, new GrammarOptions().skipRule("GAP"));
        ParseOutput out = g.parse("abc  def");
        assertTrue(out.success());
        assertEquals("abcdef", out.tree().text());
        assertTrue(out.tokens().stream().anyMatch(tk -> tk.kind() instanceof TokenKind.Skip));
    }

    @Test
    void implicitMultiCharLiteral() {
        Grammar g = Grammar.compile("""
                @start r;
                r : 'true' | 'false' ;
                """);
        ParseOutput out = g.parse("true");
        assertTrue(out.success());
        assertEquals("true", out.tree().text());
        assertInstanceOf(TokenKind.Terminal.class, out.tokens().get(0).kind());
        assertEquals("true", out.tokens().get(0).typeName());
        assertFalse(g.tryParse("truex").success());
    }

    @Test
    void emptyInputParses() {
        Grammar g = Grammar.compile("""
                @start r;
                r : A* ;
                A : [a-z] ;
                """);
        assertTrue(g.tryParse("").success());
        assertEquals("", g.parse("").tree().text());
        assertTrue(g.tryParse("ab").success());
    }

    @Test
    void lexerErrorReportsColumn() {
        Grammar g = Grammar.compile("""
                @start r;
                r : ID ;
                ID : [a-z]+ ;
                """);
        ParseException ex = assertThrows(ParseException.class, () -> g.parse("abc!"));
        assertEquals(4, ex.column());
    }

    @Test
    void syntacticPredicates() {
        Grammar g = Grammar.compile("""
                @start word;
                word : ident ;
                ident : &(!Keyword) ID ;
                Keyword : 'if' | 'else' ;
                ID : [a-z]+ ;
                WS : [ \\t]+ -> kind(SKIP) ;
                """);
        assertTrue(g.tryParse("name").success());
        // "if" 是 Keyword，&(!Keyword) 前瞻失败 → ident 不匹配
        assertFalse(g.tryParse("if").success());
    }

    @Test
    void recognizerApi() {
        Grammar g = Grammar.compile(PAREN);
        assertEquals("s", g.entry());
        Recognizer r = g.recognizer("((1))");
        assertTrue(r.matches());
        assertEquals("((1))", r.tree().text());
        assertEquals(5, r.end());
        // lookingAt：前缀匹配，不要求到末尾（尾部 '(' 词法可识别，但解析未到 EOF）
        Recognizer r2 = g.recognizer("((1))(");
        assertTrue(r2.lookingAt());
        assertFalse(r2.matches());
        // region：限定子区间
        Recognizer r3 = g.recognizer("xx((1))yy").region(2, 7);
        assertTrue(r3.matches());
    }

    @Test
    void visitorWalksTree() {
        Grammar g = Grammar.compile(PAREN);
        ParseTree t = g.parse("((1))").tree();
        List<String> names = new ArrayList<>();
        ParseTreeVisitor<Void> v = new ParseTreeVisitor<>() {
            @Override
            public Void visit(ParseTree node) {
                names.add(node.name());
                return visitChildren(node);
            }
        };
        v.visit(t);
        assertTrue(names.contains("s"));
        assertTrue(names.contains("N"));
    }

    @Test
    void treeNavigation() {
        ParseTree t = Grammar.compile(PAREN).parse("((1))").tree();
        assertEquals(3, t.children().size()); // '('、s、')'
        assertEquals("(", t.children().get(0).name());
        assertTrue(t.children().get(1).isLeaf() == false);
        assertTrue(t.children().get(0).isLeaf());
    }

    @Test
    void repeatPlusAndOptional() {
        Grammar g = Grammar.compile("""
                @start r;
                r : A+ B? ;
                A : [a-z] ;
                B : [0-9] ;
                """);
        assertTrue(g.tryParse("abc1").success());
        assertTrue(g.tryParse("abc").success());
        assertFalse(g.tryParse("abc12").success());
    }

    @Test
    void tokenPositions() {
        Grammar g = Grammar.compile("""
                @start r;
                r : ID ID ;
                ID : [a-z]+ ;
                WS : [\\n]+ -> kind(SKIP) ;
                """);
        List<Token> toks = g.parse("ab\ncd").tokens();
        assertEquals(1, toks.get(0).line());
        assertEquals(1, toks.get(0).column());
        assertEquals(0, toks.get(0).start());
        assertEquals(2, toks.get(0).end());
        // 换行后的 ID 在第 2 行第 1 列
        assertEquals(2, toks.get(2).line());
        assertEquals(1, toks.get(2).column());
    }

    @Test
    void fragmentProducesNoToken() {
        Grammar g = Grammar.compile("""
                @start r;
                r : HEX ;
                fragment DIG : [0-9] ;
                fragment LET : [a-f] ;
                HEX : (DIG | LET)+ ;
                """);
        ParseOutput out = g.parse("1ab");
        assertTrue(out.success());
        assertTrue(out.tokens().stream().noneMatch(tk -> tk.typeName().equals("DIG")));
        assertTrue(out.tokens().stream().noneMatch(tk -> tk.typeName().equals("LET")));
    }

    @Test
    void multipleLabels() {
        Grammar g = Grammar.compile("""
                @start r;
                r : A ':' B # pair | A # lone ;
                A : [a-z]+ ;
                B : [0-9]+ ;
                """);
        assertEquals("pair", g.parse("x:1").tree().name());
        assertEquals("lone", g.parse("x").tree().name());
    }

    @Test
    void precedenceAndAssociativity() {
        Grammar g = Grammar.compile("""
                @start expr;
                expr  : term (('+' | '-') term)* ;
                term  : factor (('*' | '/') factor)* ;
                factor: Number | '(' expr ')' | '-' factor ;
                Number: [0-9]+ -> kind(NUMBER_LITERAL) ;
                WS    : [ \\t\\n]+ -> kind(SKIP) ;
                """);
        ParseTree add = g.parse("1+2*3").tree();
        assertEquals("1+2*3", add.text());
        // 乘法嵌套在加法之下的 term 里
        assertTrue(add.children().stream()
                .anyMatch(c -> c.name().equals("term") && hasChild(c, "*")));
        ParseTree sub = g.parse("1-2-3").tree();
        assertEquals("1-2-3", sub.text());
    }

    private static boolean hasChild(ParseTree node, String name) {
        return node.children().stream().anyMatch(c -> c.name().equals(name));
    }
}
