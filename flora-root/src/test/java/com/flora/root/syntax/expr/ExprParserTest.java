package com.flora.root.syntax.expr;

import com.flora.root.syntax.common.Tokenizer;
import com.flora.root.syntax.common.definition.Token;
import com.flora.root.syntax.common.definition.TokenKind;
import com.flora.root.syntax.common.exceptions.SyntaxException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ExprParser} / {@link ExprProgram} / {@link Semantics} / {@link Tokenizer} 测试。
 */
class ExprParserTest {

    // ── AST 结构 ──

    @Test
    void arithmeticPriority() {
        Expr e = ExprParser.parse("1+2*3");
        assertTrue(e instanceof Expr.Binary b
                && b.op().equals("+")
                && b.left() instanceof Expr.Number n && n.value().equals("1")
                && b.right() instanceof Expr.Binary r && r.op().equals("*"));
    }

    @Test
    void parentheses() {
        Expr e = ExprParser.parse("(1+2)*3");
        assertTrue(e instanceof Expr.Binary b
                && b.op().equals("*")
                && b.left() instanceof Expr.Binary l && l.op().equals("+"));
    }

    @Test
    void comparisonPriority() {
        Expr e = ExprParser.parse("1<2==3");
        // == 优先级低于 <：应解析为 ==(<(1,2), 3)
        assertTrue(e instanceof Expr.Binary b
                && b.op().equals("==")
                && b.left() instanceof Expr.Binary l && l.op().equals("<"));
    }

    @Test
    void bitwisePriority() {
        Expr e = ExprParser.parse("1<<2|3");
        assertTrue(e instanceof Expr.Binary b
                && b.op().equals("|")
                && b.left() instanceof Expr.Binary l && l.op().equals("<<"));
    }

    @Test
    void logicalPriority() {
        Expr e = ExprParser.parse("a&&b||c");
        assertTrue(e instanceof Expr.Binary b
                && b.op().equals("||")
                && b.left() instanceof Expr.Binary l && l.op().equals("&&"));
    }

    @Test
    void unaryAndIdent() {
        assertTrue(ExprParser.parse("-x") instanceof Expr.Unary u
                && u.op().equals("-") && u.operand() instanceof Expr.Ident);
        assertTrue(ExprParser.parse("!a") instanceof Expr.Unary u && u.op().equals("!"));
        assertTrue(ExprParser.parse("~1") instanceof Expr.Unary u && u.op().equals("~"));
    }

    @Test
    void longestMatch() {
        assertTrue(ExprParser.parse("a>>>b") instanceof Expr.Binary b && b.op().equals(">>>"));
        assertTrue(ExprParser.parse("a&&b") instanceof Expr.Binary b && b.op().equals("&&"));
        assertTrue(ExprParser.parse("a<=b") instanceof Expr.Binary b && b.op().equals("<="));
        assertTrue(ExprParser.parse("a==b") instanceof Expr.Binary b && b.op().equals("=="));
    }

    // ── 新字面量 ──

    @Test
    void stringLiteral() {
        Expr e = ExprParser.parse("\"hi\"");
        assertTrue(e instanceof Expr.Str s && s.value().equals("hi"));
    }

    @Test
    void boolLiteral() {
        assertTrue(ExprParser.parse("true") instanceof Expr.Bool b && b.value());
        assertTrue(ExprParser.parse("false") instanceof Expr.Bool b && !b.value());
    }

    // ── 三元 ──

    @Test
    void ternaryNode() {
        Expr e = ExprParser.parse("1?2:3");
        assertTrue(e instanceof Expr.Ternary t
                && t.cond() instanceof Expr.Number
                && t.whenTrue() instanceof Expr.Number
                && t.whenFalse() instanceof Expr.Number);
    }

    @Test
    void ternaryRightAssoc() {
        Expr e = ExprParser.parse("a?b:c?d:e");
        assertTrue(e instanceof Expr.Ternary outer
                && outer.whenFalse() instanceof Expr.Ternary); // 右结合
    }

    // ── 函数调用 ──

    @Test
    void callNode() {
        Expr e = ExprParser.parse("max(3,5)");
        assertTrue(e instanceof Expr.Call c
                && c.name().equals("max")
                && c.args().size() == 2);
    }

    @Test
    void nestedCall() {
        Expr e = ExprParser.parse("max(min(1,2),3)");
        assertTrue(e instanceof Expr.Call c
                && c.args().get(0) instanceof Expr.Call);
    }

    // ── 位置跟踪 ──

    @Test
    void positionTracked() {
        Expr e = ExprParser.parse("1+2");
        assertEquals(0, ((Expr.Binary) e).left().pos());
        assertEquals(1, e.pos()); // '+' 位置
    }

    // ── 默认数值语义 ──

    @Test
    void defaultArithmetic() {
        assertEquals(7, ExprParser.evaluate("1+2*3", Semantics.intArithmetic()));
        assertEquals(9, ExprParser.evaluate("(1+2)*3", Semantics.intArithmetic()));
        assertEquals(7, ExprParser.evaluate("1<<2|3", Semantics.intArithmetic()));
    }

    @Test
    void defaultComparison() {
        assertEquals(1, ExprParser.evaluate("1<2", Semantics.intArithmetic()));
        assertEquals(0, ExprParser.evaluate("2<1", Semantics.intArithmetic()));
        assertEquals(1, ExprParser.evaluate("1==1", Semantics.intArithmetic()));
        assertEquals(0, ExprParser.evaluate("1!=1", Semantics.intArithmetic()));
        assertEquals(1, ExprParser.evaluate("2>=2", Semantics.intArithmetic()));
    }

    @Test
    void defaultUnary() {
        assertEquals(-5, ExprParser.evaluate("-5", Semantics.intArithmetic()));
        assertEquals(0, ExprParser.evaluate("!1", Semantics.intArithmetic()));
    }

    @Test
    void defaultTernary() {
        assertEquals(2, ExprParser.evaluate("1?2:3", Semantics.intArithmetic()));
        assertEquals(3, ExprParser.evaluate("0?2:3", Semantics.intArithmetic()));
        assertEquals(2, ExprParser.evaluate("1<2?2:3", Semantics.intArithmetic()));
    }

    @Test
    void defaultBool() {
        assertEquals(1, ExprParser.evaluate("true", Semantics.intArithmetic()));
        assertEquals(0, ExprParser.evaluate("false", Semantics.intArithmetic()));
    }

    // ── 短路逻辑 ──

    @Test
    void shortCircuitAnd() {
        // 0 && (1/0)：右操作数不消费，不抛 ArithmeticException
        assertEquals(0, ExprParser.evaluate("0&&(1/0)", Semantics.intArithmetic()));
    }

    @Test
    void shortCircuitOr() {
        // 1 || (1/0)：右操作数不消费
        assertEquals(1, ExprParser.evaluate("1||(1/0)", Semantics.intArithmetic()));
    }

    @Test
    void shortCircuitTernary() {
        // 条件成立时只求值 whenTrue 分支
        assertEquals(2, ExprParser.evaluate("1?2:(1/0)", Semantics.intArithmetic()));
        assertEquals(3, ExprParser.evaluate("0?(1/0):3", Semantics.intArithmetic()));
    }

    // ── 语义注入 ──

    @Test
    void semanticsInjection() {
        ExprProgram prog = ExprParser.compile("1+2*3");
        assertEquals(7, prog.evaluate(Semantics.intArithmetic()));
        Semantics<String> concat = Semantics.<String>builder()
                .onNumber(String::valueOf)
                .onBinary("+", (a, right) -> "(" + a + "+" + right.get() + ")")
                .onBinary("*", (a, right) -> "(" + a + "*" + right.get() + ")")
                .build();
        assertEquals("(1+(2*3))", prog.evaluate(concat));
    }

    @Test
    void functionInjection() {
        Semantics<Integer> s = Semantics.<Integer>builder()
                .onNumber(Integer::parseInt)
                .onCall((name, args) -> "max".equals(name)
                        ? Math.max(args.getFirst(), args.get(1))
                        : args.getFirst() + args.get(1))
                .build();
        assertEquals(5, ExprParser.evaluate("max(3,5)", s));
        assertEquals(8, ExprParser.evaluate("sum(3,5)", s));
    }

    @Test
    void missingSemanticsThrows() {
        Semantics<Integer> partial = Semantics.<Integer>builder()
                .onNumber(Integer::parseInt)
                .build();
        assertThrows(SyntaxException.class, () -> ExprParser.evaluate("1+2", partial));
        assertThrows(SyntaxException.class, () -> ExprParser.evaluate("1?2:3", partial));
        assertThrows(SyntaxException.class, () -> ExprParser.evaluate("\"hi\"", partial));
    }

    // ── visitor ──

    @Test
    void visitorEvaluates() {
        // 用 visitor 重算 1+2*3
        ExprVisitor<Integer> v = new ExprVisitor<>() {
            @Override
            public Integer number(String value) {
                return Integer.parseInt(value);
            }

            @Override
            public Integer ident(String name) {
                return 0;
            }

            @Override
            public Integer string(String value) {
                return 0;
            }

            @Override
            public Integer bool(boolean value) {
                return value ? 1 : 0;
            }

            @Override
            public Integer unary(String op, Integer operand) {
                return switch (op) {
                    case "-" -> -operand;
                    case "!" -> operand == 0 ? 1 : 0;
                    case "~" -> ~operand;
                    default -> 0;
                };
            }

            @Override
            public Integer binary(String op, Integer left, Integer right) {
                return switch (op) {
                    case "+" -> left + right;
                    case "*" -> left * right;
                    default -> 0;
                };
            }

            @Override
            public Integer ternary(Integer cond, Integer whenTrue, Integer whenFalse) {
                return cond != 0 ? whenTrue : whenFalse;
            }

            @Override
            public Integer call(String name, List<Integer> args) {
                return 0;
            }
        };
        assertEquals(7, ExprParser.parse("1+2*3").accept(v));
    }

    @Test
    void visitorCountsNodes() {
        AtomicInteger count = new AtomicInteger();
        ExprVisitor<Integer> counter = new ExprVisitor<>() {
            @Override
            public Integer number(String value) {
                count.incrementAndGet();
                return 0;
            }

            @Override
            public Integer ident(String name) {
                count.incrementAndGet();
                return 0;
            }

            @Override
            public Integer string(String value) {
                count.incrementAndGet();
                return 0;
            }

            @Override
            public Integer bool(boolean value) {
                count.incrementAndGet();
                return 0;
            }

            @Override
            public Integer unary(String op, Integer operand) {
                count.incrementAndGet();
                return 0;
            }

            @Override
            public Integer binary(String op, Integer left, Integer right) {
                count.incrementAndGet();
                return 0;
            }

            @Override
            public Integer ternary(Integer cond, Integer whenTrue, Integer whenFalse) {
                count.incrementAndGet();
                return 0;
            }

            @Override
            public Integer call(String name, List<Integer> args) {
                count.incrementAndGet();
                return 0;
            }
        };
        ExprParser.parse("1+2*3").accept(counter);
        assertEquals(5, count.get()); // 1, 2, 3, *, +
    }

    // ── 共享 Tokenizer ──

    @Test
    void sharedTokenizer() {
        Tokenizer t = Tokenizer.of(">>>", "<<", "&&", "+");
        List<Token> tokens = t.tokenize("a<<b");
        assertEquals(TokenKind.IDENTIFIER, tokens.get(0).kind());
        assertEquals("a", tokens.get(0).text());
        assertEquals(TokenKind.OPERATOR, tokens.get(1).kind());
        assertEquals("<<", tokens.get(1).text());
        assertEquals(TokenKind.IDENTIFIER, tokens.get(2).kind());
        assertEquals(TokenKind.EOF, tokens.get(3).kind());
    }

    @Test
    void tokenizerStringLiteral() {
        List<Token> tokens = Tokenizer.of("+").tokenize("\"hi\" + x");
        assertEquals(TokenKind.STRING_LITERAL, tokens.get(0).kind());
        assertEquals("\"hi\"", tokens.get(0).text());
        assertEquals(TokenKind.OPERATOR, tokens.get(1).kind());
        assertEquals("+", tokens.get(1).text());
        assertEquals(TokenKind.IDENTIFIER, tokens.get(2).kind()); // x
    }

    // ── 非法输入 ──

    @Test
    void invalidThrows() {
        assertThrows(SyntaxException.class, () -> ExprParser.parse("1+"));
        assertThrows(SyntaxException.class, () -> ExprParser.parse("(a"));
        assertThrows(SyntaxException.class, () -> ExprParser.parse("a)"));
        assertThrows(SyntaxException.class, () -> ExprParser.parse("1 2"));
        assertThrows(SyntaxException.class, () -> ExprParser.parse("f(1"));
        assertThrows(SyntaxException.class, () -> ExprParser.parse("1?2"));
    }

    // ── ExprProgram AST 访问 ──

    @Test
    void programAstAccess() {
        ExprProgram prog = ExprParser.compile("a+b");
        assertTrue(prog.ast() instanceof Expr.Binary b && b.op().equals("+"));
        assertEquals("(a + b)", prog.toString());
    }

    // ── 短路副作用验证（第 4 项）──

    @Test
    void shortCircuitAndSideEffect() {
        AtomicInteger sideEffect = new AtomicInteger();
        Semantics<Integer> s = Semantics.<Integer>builder()
                .onNumber(Integer::parseInt)
                .onBinary("/", (a, right) -> {
                    sideEffect.incrementAndGet();
                    return a / right.get();
                })
                .onBinary("&&", (a, right) -> a != 0 ? right.get() : 0)
                .build();
        assertEquals(0, ExprParser.evaluate("0&&(1/0)", s));
        assertEquals(0, sideEffect.get(), "&& 短路时右操作数不应被求值");
    }

    @Test
    void shortCircuitOrSideEffect() {
        AtomicInteger sideEffect = new AtomicInteger();
        Semantics<Integer> s = Semantics.<Integer>builder()
                .onNumber(Integer::parseInt)
                .onBinary("/", (a, right) -> {
                    sideEffect.incrementAndGet();
                    return a / right.get();
                })
                .onBinary("||", (a, right) -> a != 0 ? 1 : right.get())
                .build();
        assertEquals(1, ExprParser.evaluate("1||(1/0)", s));
        assertEquals(0, sideEffect.get(), "|| 短路时右操作数不应被求值");
    }

    @Test
    void shortCircuitTernarySideEffect() {
        AtomicInteger sideEffect = new AtomicInteger();
        Semantics<Integer> s = Semantics.<Integer>builder()
                .onNumber(Integer::parseInt)
                .onBinary("/", (a, right) -> {
                    sideEffect.incrementAndGet();
                    return a / right.get();
                })
                .onTernary((cond, t, f) -> cond != 0 ? t.get() : f.get())
                .build();
        assertEquals(2, ExprParser.evaluate("1?2:(1/0)", s));
        assertEquals(0, sideEffect.get(), "三元未选中分支不应被求值");
        assertEquals(3, ExprParser.evaluate("0?(1/0):3", s));
        assertEquals(0, sideEffect.get(), "三元未选中分支不应被求值");
    }

    // ── 错误定位（第 6 项）──

    @Test
    void ternaryMissingColonReportsQuestionPos() {
        SyntaxException e = assertThrows(SyntaxException.class, () -> ExprParser.parse("1?2"));
        assertTrue(e.getMessage().contains("位置 1"), "应报 '?' 位置: " + e.getMessage());
    }

    @Test
    void callMissingParenReportsCallNamePos() {
        SyntaxException e = assertThrows(SyntaxException.class, () -> ExprParser.parse("f(1"));
        assertTrue(e.getMessage().contains("位置 0"), "应报函数名位置: " + e.getMessage());
    }

    @Test
    void parenMissingCloseReportsOpenParenPos() {
        SyntaxException e = assertThrows(SyntaxException.class, () -> ExprParser.parse("(a"));
        assertTrue(e.getMessage().contains("位置 0"), "应报 '(' 位置: " + e.getMessage());
    }

    // ── prettyPrint（第 8 项）──

    @Test
    void prettyPrintNoRedundantParens() {
        assertEquals("1 + 2 * 3", ExprParser.compile("1+2*3").prettyPrint());
    }

    @Test
    void prettyPrintKeepsNeededParens() {
        assertEquals("(1 + 2) * 3", ExprParser.compile("(1+2)*3").prettyPrint());
    }

    @Test
    void prettyPrintTernary() {
        assertEquals("1 ? 2 : 3", ExprParser.compile("1?2:3").prettyPrint());
    }

    @Test
    void prettyPrintUnaryBinary() {
        assertEquals("-1 + 2", ExprParser.compile("-1+2").prettyPrint());
        assertEquals("-(1 + 2)", ExprParser.compile("-(1+2)").prettyPrint());
    }

    @Test
    void toStringStillFullParens() {
        assertEquals("(1 + (2 * 3))", ExprParser.compile("1+2*3").toString());
    }
}
