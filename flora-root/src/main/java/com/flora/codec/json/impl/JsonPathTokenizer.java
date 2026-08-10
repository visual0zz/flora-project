package com.flora.codec.json.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC 9535 JSONPath 词法分析器。
 * <p>单遍扫描，将 JSONPath 表达式字符串拆分为 {@link Token} 流。</p>
 */
public final class JsonPathTokenizer {

    private final String src;
    private int pos;

    private JsonPathTokenizer(String src) {
        this.src = src;
        this.pos = 0;
    }

    public static List<Token> tokenize(String src) {
        JsonPathTokenizer t = new JsonPathTokenizer(src);
        List<Token> tokens = new ArrayList<>();
        while (t.pos < t.src.length()) {
            char c = t.src.charAt(t.pos);
            int start = t.pos;
            switch (c) {
                case '$': t.emit(tokens, TokenType.ROOT, "$", start); break;
                case '@': t.emit(tokens, TokenType.CURRENT, "@", start); break;
                case '*': t.emit(tokens, TokenType.STAR, "*", start); break;
                case '[': t.emit(tokens, TokenType.LBRACKET, "[", start); break;
                case ']': t.emit(tokens, TokenType.RBRACKET, "]", start); break;
                case '(': t.emit(tokens, TokenType.LPAREN, "(", start); break;
                case ')': t.emit(tokens, TokenType.RPAREN, ")", start); break;
                case ':': t.emit(tokens, TokenType.COLON, ":", start); break;
                case ',': t.emit(tokens, TokenType.COMMA, ",", start); break;
                case '?': t.emit(tokens, TokenType.QUESTION, "?", start); break;
                case '!': {
                    if (t.pos + 1 < t.src.length() && t.src.charAt(t.pos + 1) == '=') {
                        t.emit(tokens, TokenType.NE, "!=", start);
                    } else {
                        t.emit(tokens, TokenType.NOT, "!", start);
                    }
                    break;
                }
                case '=': {
                    if (t.pos + 1 < t.src.length() && t.src.charAt(t.pos + 1) == '=') {
                        t.emit(tokens, TokenType.EQ, "==", start);
                    } else throw t.err("期望 '=='");
                    break;
                }
                case '<': {
                    if (t.pos + 1 < t.src.length() && t.src.charAt(t.pos + 1) == '=') {
                        t.emit(tokens, TokenType.LE, "<=", start);
                    } else {
                        t.emit(tokens, TokenType.LT, "<", start);
                    }
                    break;
                }
                case '>': {
                    if (t.pos + 1 < t.src.length() && t.src.charAt(t.pos + 1) == '=') {
                        t.emit(tokens, TokenType.GE, ">=", start);
                    } else {
                        t.emit(tokens, TokenType.GT, ">", start);
                    }
                    break;
                }
                case '&': {
                    if (t.advanceIf('&')) { t.emit(tokens, TokenType.AND, "&&", start); t.pos++; }
                    else throw t.err("期望 '&&'");
                    break;
                }
                case '|': {
                    if (t.advanceIf('|')) { t.emit(tokens, TokenType.OR, "||", start); t.pos++; }
                    else throw t.err("期望 '||'");
                    break;
                }
                case '.':
                    if (t.pos + 1 < t.src.length() && t.src.charAt(t.pos + 1) == '.') {
                        t.emit(tokens, TokenType.DOT_DOT, "..", start);
                    } else {
                        t.emit(tokens, TokenType.DOT, ".", start);
                    }
                    break;
                case '\'': t.readString(tokens, start); break;
                case ' ':
                case '\t':
                case '\n':
                case '\r':
                    t.pos++;
                    break;
                default:
                    if (isDigitStart(c) || (c == '-' && t.pos + 1 < t.src.length()
                            && isDigitStart(t.src.charAt(t.pos + 1)))) {
                        t.readNumber(tokens, start);
                    } else if (isIdentStart(c)) {
                        t.readIdent(tokens, start);
                    } else {
                        throw t.err("非法字符 '" + c + "'");
                    }
            }
        }
        tokens.add(new Token(TokenType.EOF, "", t.pos));
        return tokens;
    }

    private void emit(List<Token> tokens, TokenType type, String value, int startPos) {
        tokens.add(new Token(type, value, startPos));
        // pos 已被 emit 的调用者推进到 value 之后，但 `$` `@` 等单字符情况需要手动推进
        if (pos == startPos) pos += value.length();
    }

    private boolean match(char c) { return pos < src.length() && src.charAt(pos) == c; }

    private boolean advanceIf(char c) {
        if (pos < src.length() && src.charAt(pos) == c) { pos++; return true; }
        return false;
    }

    private void readString(List<Token> tokens, int start) {
        pos++; // 跳过 '
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\'') { pos++; break; }
            if (c == '\\') { pos++; if (pos < src.length()) sb.append(src.charAt(pos++)); }
            else sb.append(c);
            pos++;
        }
        tokens.add(new Token(TokenType.STRING, sb.toString(), start));
    }

    private void readNumber(List<Token> tokens, int start) {
        if (src.charAt(pos) == '-') pos++;
        while (pos < src.length() && isDigit(src.charAt(pos))) pos++;
        if (pos < src.length() && src.charAt(pos) == '.') {
            pos++;
            while (pos < src.length() && isDigit(src.charAt(pos))) pos++;
        }
        tokens.add(new Token(TokenType.NUMBER, src.substring(start, pos), start));
    }

    private void readIdent(List<Token> tokens, int start) {
        while (pos < src.length() && isIdentPart(src.charAt(pos))) pos++;
        String word = src.substring(start, pos);
        switch (word) {
            case "true":  tokens.add(new Token(TokenType.TRUE, word, start)); break;
            case "false": tokens.add(new Token(TokenType.FALSE, word, start)); break;
            case "null":  tokens.add(new Token(TokenType.NULL, word, start)); break;
            case "length":
            case "count":
                tokens.add(new Token(TokenType.FUNCTION, word, start)); break;
            default:
                tokens.add(new Token(TokenType.NAME, word, start));
        }
    }

    private static boolean isDigitStart(char c) { return c >= '0' && c <= '9'; }
    private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }
    private static boolean isIdentStart(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'; }
    private static boolean isIdentPart(char c) { return isIdentStart(c) || isDigit(c); }

    private IllegalStateException err(String msg) {
        return new IllegalStateException("JSONPath 词法错误 @" + pos + ": " + msg);
    }
}
