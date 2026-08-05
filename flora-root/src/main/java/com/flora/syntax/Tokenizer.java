package com.flora.syntax;

import com.flora.syntax.definition.Token;
import com.flora.syntax.definition.TokenKind;
import com.flora.syntax.exceptions.SyntaxException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 共享通用词法器：把输入字符串扫描为 {@link Token} 流。
 * <p>通用扫描数字（含小数/科学计数）、标识符、字符串字面量、空白跳过；符号/运算符由构造参数指定
 * （按长度从长到短匹配，最长优先），括号 {@code (}/{@code )} 固定产出 {@code kind=Terminal} 的 token。
 * token 的 {@code text} 为输入原文（字符串字面量含引号与转义，不解码）。实例可复用（线程安全，无内部状态）。</p>
 *
 * <pre>{@code
 * Tokenizer t = Tokenizer.of("+", "-", "<<", "&&");
 * List<Token> tokens = t.tokenize("a + b << 2");
 * }</pre>
 */
public final class Tokenizer {

    /** 默认符号集合（空）：仅数字/标识符/字符串/括号。 */
    public static final Tokenizer PLAIN = Tokenizer.of();

    private final String[] symbols; // 按长度降序

    private Tokenizer(String[] symbols) {
        this.symbols = symbols;
    }

    /** 创建词法器，符号按最长优先匹配。 */
    public static Tokenizer of(String... symbols) {
        String[] sorted = Arrays.stream(symbols)
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toArray(String[]::new);
        return new Tokenizer(sorted);
    }

    /** 扫描输入为 token 流（末尾含 EOF token）。 */
    public List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int pos = 0;
        int line = 1;
        int col = 1;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isWhitespace(c)) {
                if (c == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
                pos++;
                continue;
            }
            int start = pos;
            if (Character.isDigit(c)) {
                int end = advanceNumber(input, pos);
                tokens.add(new Token(TokenKind.NUMBER_LITERAL, "NUMBER",
                        input.substring(start, end), start, end, line, col));
                pos = end;
            } else if (Character.isLetter(c) || c == '_') {
                int end = advanceIdent(input, pos);
                tokens.add(new Token(TokenKind.IDENTIFIER, "IDENT",
                        input.substring(start, end), start, end, line, col));
                pos = end;
            } else if (c == '"' || c == '\'') {
                int end = advanceString(input, pos);
                tokens.add(new Token(TokenKind.STRING_LITERAL, "TEXT",
                        input.substring(start, end), start, end, line, col));
                pos = end;
            } else {
                String sym = matchSymbol(input, pos);
                if (sym != null) {
                    boolean paren = sym.equals("(") || sym.equals(")");
                    tokens.add(new Token(paren ? TokenKind.TERMINAL : TokenKind.OPERATOR,
                            paren ? sym : "SYMBOL", sym, start, start + sym.length(), line, col));
                    pos += sym.length();
                } else if (c == '(') {
                    tokens.add(new Token(TokenKind.TERMINAL, "(", "(", start, start + 1, line, col));
                    pos++;
                } else if (c == ')') {
                    tokens.add(new Token(TokenKind.TERMINAL, ")", ")", start, start + 1, line, col));
                    pos++;
                } else {
                    throw SyntaxException.at(start, "无法识别的字符: " + c);
                }
            }
            int[] lc = advanceLineCol(input, start, pos, line, col);
            line = lc[0];
            col = lc[1];
        }
        tokens.add(new Token(TokenKind.EOF, "EOF", "", pos, pos, line, col));
        return tokens;
    }

    // ── 读取辅助（返回推进后的位置）──

    /** 推进 line/col：统计子串中的换行与字符数。 */
    private static int[] advanceLineCol(String input, int from, int to, int line, int col) {
        for (int k = from; k < to; k++) {
            if (input.charAt(k) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new int[]{line, col};
    }

    private String matchSymbol(String input, int pos) {
        for (String sym : symbols) {
            if (input.startsWith(sym, pos)) {
                return sym;
            }
        }
        return null;
    }

    private static int advanceNumber(String input, int pos) {
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (pos + 1 < input.length() && input.charAt(pos) == '.'
                && Character.isDigit(input.charAt(pos + 1))) {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }
        if (pos + 1 < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            int p = pos + 1;
            if (p < input.length() && (input.charAt(p) == '+' || input.charAt(p) == '-')) {
                p++;
            }
            if (p < input.length() && Character.isDigit(input.charAt(p))) {
                pos = p;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            }
        }
        return pos;
    }

    private static int advanceIdent(String input, int pos) {
        while (pos < input.length()
                && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }
        return pos;
    }

    /** 推进到字符串字面量的闭合引号之后；未闭合抛 {@link SyntaxException}。 */
    private static int advanceString(String input, int pos) {
        char quote = input.charAt(pos);
        pos++;
        while (pos < input.length() && input.charAt(pos) != quote) {
            if (input.charAt(pos) == '\\' && pos + 1 < input.length()) {
                pos += 2;
            } else {
                pos++;
            }
        }
        if (pos >= input.length()) {
            throw SyntaxException.at(pos, "字符串字面量未闭合");
        }
        return pos + 1;
    }
}
