package com.flora.codec.json.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RFC 9535 JSONPath 语法分析器。
 * <p>递归下降解析 {@link Token} 流，构建 {@link Selector} 列表。</p>
 */
public final class JsonPathParser {

    private final List<Token> tokens;
    private int pos;

    private JsonPathParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static List<Selector> parse(List<Token> tokens) {
        JsonPathParser p = new JsonPathParser(tokens);
        p.expect(TokenType.ROOT);
        List<Selector> selectors = new ArrayList<>();
        while (p.peek().type() != TokenType.EOF) {
            selectors.add(p.parseSelector());
        }
        return selectors;
    }

    /** 解析过滤器表达式（用于 FilterSelector 内部）。 */
    public static FilterExpr parseFilter(List<Token> tokens) {
        JsonPathParser p = new JsonPathParser(tokens);
        FilterExpr expr = p.parseOr();
        if (p.peek().type() != TokenType.EOF) {
            throw p.err("过滤器表达式后有多余 token");
        }
        return expr;
    }

    // ===================== 选择器解析 =====================

    private Selector parseSelector() {
        Token t = peek();
        return switch (t.type()) {
            case DOT -> { advance(); String n = expectNameOrWildcard(); yield parseDotSuffix(n); }
            case DOT_DOT -> { advance(); String n = expectNameOrWildcard(); yield new DescendantSelector(n); }
            case LBRACKET -> parseBracket();
            default -> throw err("期望选择器，得到 " + t);
        };
    }

    private String expectNameOrWildcard() {
        Token t = peek();
        if (t.type() == TokenType.NAME || t.type() == TokenType.FUNCTION) {
            String v = t.value(); advance(); return v;
        }
        if (t.type() == TokenType.STAR) { advance(); return null; }
        throw err("期望属性名或 *");
    }

    /** .name → NameSelector。但处理后续可能有括号的情况：不是标准的 RFC 9535 但我们支持 .name[0] 链式。 */
    private Selector parseDotSuffix(String name) {
        return new NameSelector(name);
    }

    private Selector parseBracket() {
        advance(); // 跳过 [
        Token t = peek();

        // [?()] 过滤器
        if (t.type() == TokenType.QUESTION) {
            advance();
            if (peek().type() == TokenType.LPAREN) advance(); // optional (
            FilterExpr expr = parseOr();
            if (peek().type() == TokenType.RPAREN) advance(); // optional )
            expect(TokenType.RBRACKET);
            return new FilterSelector(expr);
        }

        // [*] 通配符
        if (t.type() == TokenType.STAR) {
            advance();
            expect(TokenType.RBRACKET);
            return new WildcardSelector();
        }

        // ['name'] 或 [name] 括号成员访问
        if (t.type() == TokenType.STRING || t.type() == TokenType.NAME) {
            String name = t.value();
            advance();
            expect(TokenType.RBRACKET);
            return new NameSelector(name);
        }

        // 切片 [start:end:step] 或 [start:end] 或 [:end]
        if (isSliceStart(t)) {
            return parseSlice();
        }

        // 索引列表 [0,1,2] 或 [0]
        return parseIndices();
    }

    private boolean isSliceStart(Token t) {
        if (t.type() == TokenType.COLON) return true;
        if (t.type() != TokenType.NUMBER) return false;
        // 查看下一个 token 是否为冒号（如果是则为切片，否则为索引）
        int saved = pos;
        advance();
        boolean hasColon = peek().type() == TokenType.COLON;
        pos = saved;
        return hasColon;
    }

    private Selector parseSlice() {
        Integer start = null, end = null, step = null;

        // 第一个值（可能是 start）
        if (peek().type() == TokenType.NUMBER) {
            start = Integer.parseInt(peek().value());
            advance();
        }
        expect(TokenType.COLON);

        // 第二个值（可能是 end）
        if (peek().type() == TokenType.NUMBER) {
            end = Integer.parseInt(peek().value());
            advance();
        }

        // 第二个冒号（步长）
        if (peek().type() == TokenType.COLON) {
            advance();
            if (peek().type() == TokenType.NUMBER) {
                step = Integer.parseInt(peek().value());
                advance();
            }
        }

        expect(TokenType.RBRACKET);
        return new SliceSelector(start, end, step);
    }

    private Selector parseIndices() {
        List<Integer> indices = new ArrayList<>();
        indices.add(parseIndex());
        while (peek().type() == TokenType.COMMA) {
            advance();
            indices.add(parseIndex());
        }
        expect(TokenType.RBRACKET);
        if (indices.size() == 1) return new IndexSelector(indices.get(0));
        return new MultiIndexSelector(indices);
    }

    private int parseIndex() {
        Token t = expect(TokenType.NUMBER);
        String v = t.value();
        boolean neg = v.startsWith("-");
        return Integer.parseInt(v);
    }

    // ===================== 过滤器表达式解析 =====================

    private FilterExpr parseOr() {
        FilterExpr left = parseAnd();
        while (peek().type() == TokenType.OR) {
            advance();
            left = new LogicalOr(left, parseAnd());
        }
        return left;
    }

    private FilterExpr parseAnd() {
        FilterExpr left = parseNot();
        while (peek().type() == TokenType.AND) {
            advance();
            left = new LogicalAnd(left, parseNot());
        }
        return left;
    }

    private FilterExpr parseNot() {
        if (peek().type() == TokenType.NOT) {
            advance();
            return new LogicalNot(parseNot());
        }
        return parseComparison();
    }

    private FilterExpr parseComparison() {
        FilterTerm left = parseTerm();
        if (isCmpOp(peek().type())) {
            CmpOp op = switch (peek().type()) {
                case EQ -> CmpOp.EQ;
                case NE -> CmpOp.NE;
                case LT -> CmpOp.LT;
                case LE -> CmpOp.LE;
                case GT -> CmpOp.GT;
                case GE -> CmpOp.GE;
                default -> throw err("期望比较操作符");
            };
            advance();
            FilterTerm right = parseTerm();
            return new Comparison(left, op, right);
        }
        return new Comparison(left, CmpOp.NE, new LiteralTerm(false));
    }

    private static boolean isCmpOp(TokenType t) {
        return t == TokenType.EQ || t == TokenType.NE || t == TokenType.LT
                || t == TokenType.LE || t == TokenType.GT || t == TokenType.GE;
    }

    private FilterTerm parseTerm() {
        Token t = peek();
        return switch (t.type()) {
            case ROOT -> { advance(); yield new PathTerm(true, parsePathTail()); }
            case CURRENT -> { advance(); yield new PathTerm(false, parsePathTail()); }
            case STRING -> { advance(); yield new LiteralTerm(t.value()); }
            case NUMBER -> {
                advance();
                String v = t.value();
                yield new LiteralTerm(v.contains(".") ? (Object) Double.parseDouble(v) : Long.parseLong(v));
            }
            case TRUE -> { advance(); yield new LiteralTerm(Boolean.TRUE); }
            case FALSE -> { advance(); yield new LiteralTerm(Boolean.FALSE); }
            case NULL -> { advance(); yield new LiteralTerm(null); }
            case LPAREN -> { advance(); FilterExpr e = parseOr(); expect(TokenType.RPAREN); yield new LiteralTerm(e); }
            case FUNCTION -> {
                advance();
                expect(TokenType.LPAREN);
                boolean abs = false;
                List<Selector> segs = Collections.emptyList();
                if (peek().type() == TokenType.ROOT || peek().type() == TokenType.CURRENT) {
                    abs = peek().type() == TokenType.ROOT;
                    advance();
                    segs = parsePathTail();
                }
                expect(TokenType.RPAREN);
                yield new FunctionTerm(t.value(), abs, segs);
            }
            default -> throw err("期望表达式项，得到 " + t);
        };
    }

    private List<Selector> parsePathTail() {
        List<Selector> segs = new ArrayList<>();
        while (peek().type() == TokenType.DOT || peek().type() == TokenType.LBRACKET) {
            segs.add(parseSelector());
        }
        return segs;
    }

    // ===================== 辅助 =====================

    private Token peek() { return tokens.get(pos); }
    private void advance() { pos++; }

    private Token expect(TokenType type) {
        Token t = peek();
        if (t.type() != type) throw err("期望 " + type + "，得到 " + t);
        advance();
        return t;
    }

    private IllegalStateException err(String msg) {
        return new IllegalStateException("JSONPath 语法错误 @" + (pos < tokens.size() ? tokens.get(pos).pos() : -1) + ": " + msg);
    }
}
