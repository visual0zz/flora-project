package com.flora.syntax.definition;

/**
 * 词法层输出：一个 token。
 *
 * <p>{@code kind} 为引擎内置通用类别（跨文法通用判断）；{@code typeName} 为匹配到的文法规则名
 * （文法内特指，简单词法器产出的 token 用类别名）。两者并存：通用归类走 {@code kind}，
 * 文法特指走 {@code typeName}。{@code text} 为输入原文（含定界符/引号，不解码）。</p>
 */
public final class Token {
    private final TokenKind kind;
    private final String typeName;
    private final String text;
    private final int start;
    private final int end;
    private final int line;
    private final int column;

    public Token(TokenKind kind, String typeName, String text,
                 int start, int end, int line, int column) {
        this.kind = kind;
        this.typeName = typeName;
        this.text = text;
        this.start = start;
        this.end = end;
        this.line = line;
        this.column = column;
    }

    public TokenKind kind() { return kind; }
    public String typeName() { return typeName; }
    public String text() { return text; }
    /** 起始字符偏移（0 基）。 */
    public int start() { return start; }
    /** 结束字符偏移（不含，0 基）。 */
    public int end() { return end; }
    /** 起始行号（1 基）。 */
    public int line() { return line; }
    /** 起始列号（1 基）。 */
    public int column() { return column; }

    @Override
    public String toString() {
        return typeName + "[" + kind + "]" + "(" + text + ")@" + line + ":" + column;
    }
}
