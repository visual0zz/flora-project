package com.flora.syntax.peg;

/** 识别期错误（输入不匹配），由 parse 抛出；携带失败位置与期望项。 */
public final class ParseException extends RuntimeException {
    private final int line;
    private final int column;
    private final int offset;
    private final String expected;

    public ParseException(String message, int line, int column, int offset, String expected) {
        super(message);
        this.line = line;
        this.column = column;
        this.offset = offset;
        this.expected = expected;
    }

    public int line() { return line; }
    public int column() { return column; }
    public int offset() { return offset; }
    /** 期望匹配的描述（规则名或字面量）。 */
    public String expected() { return expected; }
}
