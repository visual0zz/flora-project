package com.flora.syntax;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 共享通用词法器：把输入字符串扫描为 {@link Token} 流。
 * <p>通用扫描数字（含小数/科学计数）、标识符、字符串字面量（含转义）、
 * 空白跳过；符号/运算符由构造参数指定（按长度从长到短匹配，最长优先），
 * 括号 {@code (}/{@code )} 固定产出 OPEN/CLOSE。实例可复用（线程安全，无内部状态）。</p>
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
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }
            int start = pos;
            if (Character.isDigit(c)) {
                tokens.add(new Token(TokenType.NUMBER, readNumber(input, pos), start));
                pos = advanceNumber(input, pos);
            } else if (Character.isLetter(c) || c == '_') {
                tokens.add(new Token(TokenType.IDENT, readIdent(input, pos), start));
                pos = advanceIdent(input, pos);
            } else if (c == '"' || c == '\'') {
                tokens.add(new Token(TokenType.TEXT, readString(input, pos), start));
                pos = advanceString(input, pos);
            } else {
                String sym = matchSymbol(input, pos);
                if (sym != null) {
                    TokenType type = switch (sym) {
                        case "(" -> TokenType.OPEN;
                        case ")" -> TokenType.CLOSE;
                        default -> TokenType.SYMBOL;
                    };
                    tokens.add(new Token(type, sym, start));
                    pos += sym.length();
                } else if (c == '(') {
                    pos++;
                    tokens.add(new Token(TokenType.OPEN, "(", start));
                } else if (c == ')') {
                    pos++;
                    tokens.add(new Token(TokenType.CLOSE, ")", start));
                } else {
                    throw SyntaxException.at(start, "无法识别的字符: " + c);
                }
            }
        }
        tokens.add(new Token(TokenType.EOF, "", input.length()));
        return tokens;
    }

    // ── 读取辅助（返回字符串或推进后的位置）──

    private String matchSymbol(String input, int pos) {
        for (String sym : symbols) {
            if (input.startsWith(sym, pos)) {
                return sym;
            }
        }
        return null;
    }

    private static String readNumber(String input, int pos) {
        int end = advanceNumber(input, pos);
        return input.substring(pos, end);
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

    private static String readIdent(String input, int pos) {
        int end = advanceIdent(input, pos);
        return input.substring(pos, end);
    }

    private static int advanceIdent(String input, int pos) {
        while (pos < input.length()
                && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }
        return pos;
    }

    /** 读字符串字面量并返回解码后的值（不含引号，处理 \\ 与转义）。 */
    private static String readString(String input, int pos) {
        char quote = input.charAt(pos);
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && input.charAt(pos) != quote) {
            char c = input.charAt(pos);
            if (c == '\\' && pos + 1 < input.length()) {
                char next = input.charAt(pos + 1);
                pos += 2;
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
                pos++;
            }
        }
        if (pos >= input.length()) {
            throw SyntaxException.at(pos, "字符串字面量未闭合");
        }
        return sb.toString();
    }

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
