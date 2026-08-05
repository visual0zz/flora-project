package com.flora.syntax.expr;

import com.flora.syntax.common.Tokenizer;
import com.flora.syntax.common.definition.Token;
import com.flora.syntax.common.definition.TokenKind;
import com.flora.syntax.common.exceptions.SyntaxException;
import com.flora.syntax.expr.impl.OpPrecedence;

import java.util.ArrayList;
import java.util.List;

/**
 * 表达式分析器：C/Java 风格运算符表达式 → {@link Expr} AST。
 * <p>递归下降 + 优先级表 {@code OpPrecedence}。对外主接口为
 * {@link #compile(String)}（编译为可复用 {@link ExprProgram}）与
 * {@link #evaluate(String, Semantics)}（一次性解析+执行）；{@link #parse(String)}
 * 直接返回 AST 供高级自定义遍历。</p>
 * <p>支持：数字/标识符/字符串/布尔字面量、一元 {@code ! ~ -}、
 * 二元（四则/位运算/比较/逻辑/移位）、三元 {@code ?:}、函数调用 {@code f(args)}、括号。</p>
 */
public final class ExprParser {

    private static final Tokenizer TOKENIZER = Tokenizer.of(
            ">>>", "<<", ">>", "<=", ">=", "==", "!=",
            "&&", "||", "+", "-", "*", "/", "%",
            "<", ">", "&", "^", "|", "!", "~", "?", ":", ",", "(");

    private final List<Token> tokens;
    private int pos;

    private ExprParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /** 解析表达式为可复用程序（不立即求值）。 */
    public static ExprProgram compile(String expression) {
        return new ExprProgram(parse(expression));
    }

    /** 一次性解析并执行。 */
    public static <T> T evaluate(String expression, Semantics<T> semantics) {
        return compile(expression).evaluate(semantics);
    }

    /** 直接解析为 AST（高级用法）。 */
    public static Expr parse(String expression) {
        ExprParser p = new ExprParser(TOKENIZER.tokenize(expression));
        Expr e = p.parseExpression();
        Token t = p.peek();
        if (t.kind() != TokenKind.EOF) {
            throw SyntaxException.at(t.start(), "表达式后有未消费的内容: " + t.text());
        }
        return e;
    }

    // ── 顶层：三元表达式（最低优先级，右结合）──

    private Expr parseExpression() {
        Expr cond = parseBinary(1);
        if (isOp(peek(), "?")) {
            int pos = advance();
            Expr whenTrue = parseExpression();
            Token colon = peek();
            if (!isOp(colon, ":")) {
                throw SyntaxException.at(pos, "三元表达式期望 ':'");
            }
            advance();
            Expr whenFalse = parseExpression();
            return new Expr.Ternary(cond, whenTrue, whenFalse, pos);
        }
        return cond;
    }

    /** 通用二元解析：按给定优先级下限循环左结合。 */
    @SuppressWarnings("InfiniteRecursion")
    private Expr parseBinary(int minLevel) {
        Expr left = parseUnary();
        while (true) {
            Token t = peek();
            if (t.kind() != TokenKind.OPERATOR) {
                break;
            }
            String op = t.text();
            if (!OpPrecedence.isBinary(op)) {
                break;
            }
            int level = OpPrecedence.level(op);
            if (level < minLevel) {
                break;
            }
            int opPos = advance();
            Expr right = parseBinary(level + 1); // 左结合：右侧用更高优先级
            left = new Expr.Binary(op, left, right, opPos);
        }
        return left;
    }

    private Expr parseUnary() {
        Token t = peek();
        if (t.kind() == TokenKind.OPERATOR && OpPrecedence.isUnary(t.text())) {
            int opPos = advance();
            return new Expr.Unary(t.text(), parseUnary(), opPos);
        }
        return parsePrimary();
    }

    private Expr parsePrimary() {
        Token t = peek();
        return switch (t.kind()) {
            case NUMBER_LITERAL -> {
                advance();
                yield new Expr.Number(t.text(), t.start());
            }
            case IDENTIFIER -> {
                advance();
                // 函数调用：标识符后跟 '('
                if (isTerminal(peek(), "(")) {
                    int callPos = t.start();
                    advance();
                    List<Expr> args = new ArrayList<>();
                    if (!isTerminal(peek(), ")")) {
                        while (true) {
                            args.add(parseExpression());
                            if (isOp(peek(), ",")) {
                                advance();
                            } else {
                                break;
                            }
                        }
                    }
                    Token close = peek();
                    if (!isTerminal(close, ")")) {
                        throw SyntaxException.at(callPos, "函数调用期望 ')'");
                    }
                    advance();
                    yield new Expr.Call(t.text(), args, callPos);
                }
                // 布尔字面量
                yield switch (t.text()) {
                    case "true" -> new Expr.Bool(true, t.start());
                    case "false" -> new Expr.Bool(false, t.start());
                    default -> new Expr.Ident(t.text(), t.start());
                };
            }
            case STRING_LITERAL -> {
                advance();
                yield new Expr.Str(decodeString(t.text()), t.start());
            }
            case TERMINAL -> {
                if (!t.text().equals("(")) {
                    throw SyntaxException.at(t.start(), "期望操作数，得到 " + t);
                }
                int openPos = advance();
                Expr inner = parseExpression();
                Token close = peek();
                if (!isTerminal(close, ")")) {
                    throw SyntaxException.at(openPos, "期望 ')'");
                }
                advance();
                yield inner;
            }
            default -> throw SyntaxException.at(t.start(), "期望操作数，得到 " + t);
        };
    }

    // ── token 流辅助 ──

    private Token peek() {
        return tokens.get(pos);
    }

    private int advance() {
        int p = pos;
        if (pos < tokens.size() - 1) {
            pos++;
        }
        return tokens.get(p).start();
    }

    /** token 是否为指定文本的运算符。 */
    private static boolean isOp(Token t, String text) {
        return t.kind() == TokenKind.OPERATOR && t.text().equals(text);
    }

    /** token 是否为指定文本的括号终端。 */
    private static boolean isTerminal(Token t, String text) {
        return t.kind() == TokenKind.TERMINAL && t.text().equals(text);
    }

    /** 解码字符串字面量原文：剥外层引号并处理转义（与共享词法器的字符串规则一致）。 */
    private static String decodeString(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < raw.length() - 1; i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length() - 1) {
                char next = raw.charAt(i + 1);
                i++;
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
