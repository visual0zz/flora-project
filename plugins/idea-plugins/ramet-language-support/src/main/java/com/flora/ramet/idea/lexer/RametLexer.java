package com.flora.ramet.idea.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RametLexer extends Lexer {

    private static final Logger LOG = Logger.getInstance(RametLexer.class);

    private enum State {
        PASSIVE, DIRECTIVE_ARGS, MACRO_ARGS, VAR_EXPR, COMMENT_BODY, DONE
    }

    private CharSequence charSequence;
    private char[] buffer;
    private int endOffset;
    private int pos;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;
    private State state;
    private int parenDepth;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.charSequence = buffer;
        this.buffer = new char[buffer.length()];
        for (int i = 0; i < buffer.length(); i++) {
            this.buffer[i] = buffer.charAt(i);
        }
        this.endOffset = endOffset;
        this.pos = startOffset;
        this.tokenStart = startOffset;
        this.tokenEnd = startOffset;
        this.tokenType = null;
        this.parenDepth = 0;
        this.state = initialState >= 0 && initialState < State.DONE.ordinal()
                ? State.values()[initialState] : State.PASSIVE;

        LOG.info("start() called: startOffset=" + startOffset + " endOffset=" + endOffset
                + " buffer.len=" + buffer.length() + " initialState=" + initialState
                + " text='" + buffer.subSequence(0, Math.min(endOffset, 50)) + "...'");

        advance();
    }

    @Override
    public void advance() {
        tokenStart = pos;
        tokenType = null;

        if (pos > endOffset) {
            LOG.warn("pos(" + pos + ") > endOffset(" + endOffset + ")");
        }

        if (pos >= endOffset) {
            state = State.DONE;
            tokenEnd = pos;
            LOG.info("advance() EOF: pos=" + pos + " endOffset=" + endOffset + " buffer.len=" + buffer.length);
            return;
        }

        switch (state) {
            case PASSIVE -> advancePassive();
            case DIRECTIVE_ARGS -> advanceTokenInDirective();
            case MACRO_ARGS -> advanceTokenInMacro();
            case VAR_EXPR -> advanceTokenInVar();
            case COMMENT_BODY -> { state = State.PASSIVE; advance(); }
            case DONE -> tokenEnd = pos;
        }

        LOG.info("advance() -> [" + tokenStart + "," + tokenEnd + ") " + tokenType
                + "  pos=" + pos + "  endOffset=" + endOffset + "  state=" + state);
    }

    // ===================================================================
    // PASSIVE
    // ===================================================================

    private void advancePassive() {
        while (pos < endOffset) {
            char c = buffer[pos];
            switch (c) {
                case '\r', '\n' -> {
                    if (pos == tokenStart) {
                        if (c == '\r' && pos + 1 < endOffset && buffer[pos + 1] == '\n') pos += 2;
                        else pos++;
                        setToken(RametTokenTypes.NEWLINE, State.PASSIVE);
                        return;
                    }
                    setToken(RametTokenTypes.PASSIVE, State.PASSIVE);
                    return;
                }
                case '\\' -> {
                    if (pos + 1 < endOffset && (buffer[pos + 1] == '$' || buffer[pos + 1] == '#')) {
                        pos += 2;
                        continue;
                    }
                    pos++;
                }
                case '$' -> {
                    if (pos + 1 < endOffset && buffer[pos + 1] == '{') {
                        if (pos > tokenStart) {
                            setToken(RametTokenTypes.PASSIVE, State.PASSIVE);
                            return;
                        }
                        pos++;
                        setToken(RametTokenTypes.DOLLAR, State.VAR_EXPR);
                        return;
                    }
                    pos++;
                }
                case '<' -> {
                    if (pos + 1 >= endOffset) { pos++; continue; }
                    char n = buffer[pos + 1];
                    if (n == '#') {
                        if (pos + 2 < endOffset && buffer[pos + 2] == '-' && buffer[pos + 3] == '-') {
                            if (pos > tokenStart) {
                                setToken(RametTokenTypes.PASSIVE, State.PASSIVE);
                                return;
                            }
                            scanComment();
                            return;
                        }
                        if (pos > tokenStart) {
                            setToken(RametTokenTypes.PASSIVE, State.PASSIVE);
                            return;
                        }
                        pos++;
                        setToken(RametTokenTypes.LT, State.DIRECTIVE_ARGS);
                        return;
                    }
                    if (n == '@') {
                        if (pos > tokenStart) {
                            setToken(RametTokenTypes.PASSIVE, State.PASSIVE);
                            return;
                        }
                        pos += 2;
                        setToken(RametTokenTypes.LT, State.MACRO_ARGS);
                        return;
                    }
                    if (n == '/' && pos + 2 < endOffset && buffer[pos + 2] == '#') {
                        if (pos > tokenStart) {
                            setToken(RametTokenTypes.PASSIVE, State.PASSIVE);
                            return;
                        }
                        int start = pos;
                        pos += 3;
                        while (pos < endOffset && Character.isJavaIdentifierPart(buffer[pos])) pos++;
                        if (pos < endOffset && buffer[pos] == '>') pos++;
                        tokenStart = start;
                        setToken(RametTokenTypes.END, State.PASSIVE);
                        return;
                    }
                    pos++;
                }
                default -> pos++;
            }
        }
        if (pos > tokenStart) {
            setToken(RametTokenTypes.PASSIVE, State.DONE);
        } else {
            LOG.warn("advancePassive() gave up early: pos=" + pos + " tokenStart=" + tokenStart + " endOffset=" + endOffset);
            state = State.DONE;
            tokenEnd = pos;
        }
    }

    // ===================================================================
    // DIRECTIVE
    // ===================================================================

    private void advanceTokenInDirective() {
        if (pos < endOffset && buffer[pos] == '#') {
            pos++;
            setToken(RametTokenTypes.HASH, State.DIRECTIVE_ARGS);
            return;
        }
        if (skipWsEmit()) return;
        int kwStart = pos;
        while (pos < endOffset && Character.isJavaIdentifierPart(buffer[pos])) pos++;
        if (pos > kwStart) {
            String word = new String(buffer, kwStart, pos - kwStart);
            setToken(keywordType(word) != null ? keywordType(word) : RametTokenTypes.IDENTIFIER, State.DIRECTIVE_ARGS);
            return;
        }
        scanArgOrClose(State.PASSIVE);
    }

    // ===================================================================
    // MACRO
    // ===================================================================

    private void advanceTokenInMacro() {
        if (skipWsEmit()) return;
        if (pos >= endOffset) { state = State.PASSIVE; return; }
        int nameStart = pos;
        while (pos < endOffset && Character.isJavaIdentifierPart(buffer[pos])) pos++;
        if (pos > nameStart) {
            setToken(RametTokenTypes.IDENTIFIER, State.MACRO_ARGS);
            return;
        }
        scanArgOrClose(State.PASSIVE);
    }

    // ===================================================================
    // VAR
    // ===================================================================

    private void advanceTokenInVar() {
        if (pos < endOffset && buffer[pos] == '{') {
            pos++;
            parenDepth = 1;
            setToken(RametTokenTypes.LBRACE, State.VAR_EXPR);
            return;
        }
        if (skipWsEmit()) return;
        if (pos >= endOffset) { state = State.PASSIVE; return; }
        scanVarToken();
    }

    // ===================================================================
    // 参数 / 闭合符
    // ===================================================================

    private void scanArgOrClose(State afterGt) {
        if (skipWsEmit()) return;
        if (pos >= endOffset) { state = State.PASSIVE; return; }

        char c = buffer[pos];
        switch (c) {
            case '"' -> { scanString(); }
            case '\'' -> { scanCharString(); }
            case '(' -> { pos++; parenDepth++; setToken(RametTokenTypes.PAREN_L, state); }
            case ')' -> { pos++; parenDepth--; setToken(RametTokenTypes.PAREN_R, state); }
            case '[' -> { pos++; parenDepth++; setToken(RametTokenTypes.PAREN_L, state); }
            case ']' -> { pos++; parenDepth--; setToken(RametTokenTypes.PAREN_R, state); }
            case '{' -> { pos++; parenDepth++; setToken(RametTokenTypes.LBRACE, state); }
            case '}' -> { pos++; parenDepth--; setToken(RametTokenTypes.RBRACE, state); }
            case ',' -> { pos++; setToken(RametTokenTypes.COMMA, state); }
            case ':' -> { pos++; setToken(RametTokenTypes.COLON, state); }
            case '.' -> {
                if (pos + 1 < endOffset && buffer[pos + 1] == '.') { pos += 2; setToken(RametTokenTypes.RANGE, state); }
                else { pos++; setToken(RametTokenTypes.DOT, state); }
            }
            case '>' -> {
                if (parenDepth > 0) { pos++; setToken(RametTokenTypes.GT, state); }
                else { pos++; setToken(RametTokenTypes.GT, afterGt); }
            }
            case '/' -> {
                if (pos + 1 < endOffset && buffer[pos + 1] == '>' && parenDepth == 0) {
                    pos++; setToken(RametTokenTypes.SLASH, state);
                } else { pos++; setToken(RametTokenTypes.OPERATOR, state); }
            }
            default -> {
                if (c == '-' || c == '+' || Character.isDigit(c)) { scanNumber(); }
                else if (Character.isJavaIdentifierStart(c)) { scanIdentOrKw(); }
                else if (isOperatorChar(c)) { pos++; setToken(RametTokenTypes.OPERATOR, state); }
                else { pos++; setToken(RametTokenTypes.OPERATOR, state); }
            }
        }
    }

    // ===================================================================
    // VAR 表达式 token
    // ===================================================================

    private void scanVarToken() {
        char c = buffer[pos];
        switch (c) {
            case '"' -> { scanString(); }
            case '{' -> { pos++; parenDepth++; setToken(RametTokenTypes.LBRACE, State.VAR_EXPR); }
            case '}' -> {
                if (parenDepth <= 1) { pos++; parenDepth = 0; setToken(RametTokenTypes.RBRACE, State.PASSIVE); }
                else { pos++; parenDepth--; setToken(RametTokenTypes.RBRACE, State.VAR_EXPR); }
            }
            case '(' -> { pos++; setToken(RametTokenTypes.PAREN_L, State.VAR_EXPR); }
            case ')' -> { pos++; setToken(RametTokenTypes.PAREN_R, State.VAR_EXPR); }
            case ',' -> { pos++; setToken(RametTokenTypes.COMMA, State.VAR_EXPR); }
            case ':' -> { pos++; setToken(RametTokenTypes.COLON, State.VAR_EXPR); }
            case '.' -> {
                if (pos + 1 < endOffset && buffer[pos + 1] == '.') { pos += 2; setToken(RametTokenTypes.RANGE, State.VAR_EXPR); }
                else { pos++; setToken(RametTokenTypes.DOT, State.VAR_EXPR); }
            }
            default -> {
                if (Character.isWhitespace(c)) { if (skipWsEmit()) return; }
                else if (c == '-' || c == '+' || Character.isDigit(c)) { scanNumber(); }
                else if (Character.isJavaIdentifierStart(c)) { scanIdentOrKw(); }
                else if (isOperatorChar(c)) { pos++; setToken(RametTokenTypes.OPERATOR, State.VAR_EXPR); }
                else { pos++; setToken(RametTokenTypes.OPERATOR, State.VAR_EXPR); }
            }
        }
    }

    // ===================================================================
    // 字面量 / 标识符
    // ===================================================================

    private void scanString() {
        tokenStart = pos;
        pos++;
        while (pos < endOffset && buffer[pos] != '"') {
            if (buffer[pos] == '\\') pos++;
            pos++;
        }
        if (pos < endOffset) pos++;
        setToken(RametTokenTypes.STRING, state);
    }

    private void scanCharString() {
        tokenStart = pos;
        pos++;
        while (pos < endOffset && buffer[pos] != '\'') {
            if (buffer[pos] == '\\') pos++;
            pos++;
        }
        if (pos < endOffset) pos++;
        setToken(RametTokenTypes.STRING, state);
    }

    private void scanNumber() {
        tokenStart = pos;
        if ((buffer[pos] == '-' || buffer[pos] == '+') && pos + 1 < endOffset
                && (Character.isDigit(buffer[pos + 1]) || buffer[pos + 1] == '.')) {
            pos++;
        }
        while (pos < endOffset && Character.isDigit(buffer[pos])) pos++;
        if (pos < endOffset && buffer[pos] == '.') {
            pos++;
            while (pos < endOffset && Character.isDigit(buffer[pos])) pos++;
        }
        if (pos < endOffset && (buffer[pos] == 'e' || buffer[pos] == 'E')) {
            pos++;
            if (pos < endOffset && (buffer[pos] == '+' || buffer[pos] == '-')) pos++;
            while (pos < endOffset && Character.isDigit(buffer[pos])) pos++;
        }
        if (pos < endOffset && "lLfFdD".indexOf(buffer[pos]) >= 0) pos++;
        setToken(RametTokenTypes.NUMBER, state);
    }

    private void scanIdentOrKw() {
        tokenStart = pos;
        while (pos < endOffset && Character.isJavaIdentifierPart(buffer[pos])) pos++;
        String word = new String(buffer, tokenStart, pos - tokenStart);
        IElementType type = switch (word) {
            case "true", "false", "null" -> RametTokenTypes.BOOLEAN;
            case "if" -> RametTokenTypes.IF;
            case "for", "list" -> RametTokenTypes.FOR;
            case "include" -> RametTokenTypes.INCLUDE;
            case "macro" -> RametTokenTypes.MACRO;
            case "else" -> RametTokenTypes.ELSE;
            case "elseif" -> RametTokenTypes.ELSEIF;
            case "continue" -> RametTokenTypes.CONTINUE;
            case "break" -> RametTokenTypes.BREAK;
            case "meta" -> RametTokenTypes.META;
            default -> RametTokenTypes.IDENTIFIER;
        };
        setToken(type, state);
    }

    // ===================================================================
    // 注释
    // ===================================================================

    private void scanComment() {
        tokenStart = pos;
        int end = findString("-->", pos + 4);
        if (end < 0) { pos = endOffset; setToken(RametTokenTypes.COMMENT, State.DONE); }
        else { pos = end + 3; setToken(RametTokenTypes.COMMENT, State.PASSIVE); }
    }

    // ===================================================================
    // 工具
    // ===================================================================

    private boolean skipWsEmit() {
        int wsStart = pos;
        while (pos < endOffset && Character.isWhitespace(buffer[pos])) pos++;
        if (pos > wsStart) {
            tokenStart = wsStart;
            setToken(RametTokenTypes.WHITESPACE, state);
            return true;
        }
        return false;
    }

    private void setToken(IElementType type, State nextState) {
        tokenEnd = pos;
        tokenType = type;
        state = nextState;
    }

    private int findString(String target, int from) {
        int tl = target.length();
        for (int k = from; k <= endOffset - tl; k++) {
            boolean found = true;
            for (int t = 0; t < tl; t++) {
                if (buffer[k + t] != target.charAt(t)) { found = false; break; }
            }
            if (found) return k;
        }
        return -1;
    }

    private static boolean isOperatorChar(char c) {
        return "=!<>+-*/%&|^~".indexOf(c) >= 0;
    }

    private static IElementType keywordType(String word) {
        return switch (word) {
            case "if" -> RametTokenTypes.IF;
            case "for", "list" -> RametTokenTypes.FOR;
            case "include" -> RametTokenTypes.INCLUDE;
            case "macro" -> RametTokenTypes.MACRO;
            case "else" -> RametTokenTypes.ELSE;
            case "elseif" -> RametTokenTypes.ELSEIF;
            case "continue" -> RametTokenTypes.CONTINUE;
            case "break" -> RametTokenTypes.BREAK;
            case "meta" -> RametTokenTypes.META;
            default -> null;
        };
    }

    // ===================================================================
    // Lexer 接口
    // ===================================================================

    @Override public @Nullable IElementType getTokenType() { return tokenType; }
    @Override public int getTokenStart() { return tokenStart; }
    @Override public int getTokenEnd() { return tokenEnd; }
    @Override public int getState() { return state.ordinal(); }
    @Override public int getBufferEnd() { return endOffset; }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return charSequence != null ? charSequence : "";
    }

    @Override
    public @NotNull CharSequence getTokenSequence() {
        if (tokenStart >= 0 && tokenEnd <= endOffset) {
            return new String(buffer, tokenStart, tokenEnd - tokenStart);
        }
        return "";
    }

    private static class Position implements LexerPosition {
        private final int offset;
        private final int state;
        Position(int offset, int state) { this.offset = offset; this.state = state; }
        @Override public int getOffset() { return offset; }
        @Override public int getState() { return state; }
    }

    @Override
    public @NotNull LexerPosition getCurrentPosition() {
        return new Position(pos, state.ordinal());
    }

    @Override
    public void restore(@NotNull LexerPosition position) {
        start(charSequence, position.getOffset(), endOffset, position.getState());
    }
}
