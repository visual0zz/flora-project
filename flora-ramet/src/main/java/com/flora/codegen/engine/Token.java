package com.flora.codegen.engine;

import java.util.List;

/**
 * 词法分析产物——由 {@link com.flora.codegen.engine.parser.Lexer} 生成的 Token 对象。
 *
 * <p>每个 Token 包含类型、原始文本、所在行号和可选的 Lson 参数列表。
 * Token 类型由 {@link Type} 枚举定义，涵盖指令关键字（IF、FOR 等）、
 * 变量引用（VAR）、纯文本（PASSIVE）和注释（COMMENT）等。
 */
public final class Token {

    public enum Type {
        /** 普通文本：不包含任何模板语法的原样文本。 */
        PASSIVE,
        /** 变量插值：{@code ${表达式}}，运行时求值并替换为结果。 */
        VAR,
        /** 注释块：{@code <#-- ... -->}，render 时跳过不输出。 */
        COMMENT,
        /** 元数据块：{@code <#meta>...<code></#meta>}，解析模板元数据。 */
        META,
        /** 条件分支开始：{@code <#if 条件>}。 */
        IF,
        /** 条件分支否则：{@code <#else>}。 */
        ELSE,
        /** 条件分支否则如果：{@code <#elseif 条件>}，用于 if 链串联。 */
        ELSEIF,
        /** 结束标签：{@code </#>}，标记指令块的结束。 */
        END,
        /** 循环开始：{@code <#for 迭代变量:表达式>}。 */
        FOR,
        /** 循环继续：{@code <#continue>}，跳过当前迭代剩余体。 */
        CONTINUE,
        /** 循环退出：{@code <#break>}，立即退出循环。 */
        BREAK,
        /** 模板包含：{@code <#include "路径">}。 */
        INCLUDE,
        /** 宏定义：{@code <#macro 名 参数...>}。 */
        MACRO,
        /** 宏调用：{@code <@宏名 参数.../>}。 */
        MACRO_CALL,
        /** 换行符：{@code \n}，渲染时保持模板的换行结构。 */
        NEWLINE
    }

    private final Token.Type type;
    private final String text;
    private final List<Object> lsonArgs;
    private final int line;
    /** 列号（从 1 开始），-1 表示未知。 */
    private final int col;
    /** 此 token 前���否有一个换行符（由 Lexer 的 pendingNewline 机制标记）。 */
    private final boolean leadingNewline;
    /** 此 token 的输出是否被抑制（后处理标记，由 Lexer 对指令后的 NEWLINE 设置）。 */
    private boolean suppressed;

    public Token(Token.Type type, String text, int line) {
        this(type, text, line, -1, null, false);
    }

    public Token(Token.Type type, String text, int line, boolean leadingNewline) {
        this(type, text, line, -1, null, leadingNewline);
    }

    public Token(Token.Type type, String text, int line, int col, boolean leadingNewline) {
        this(type, text, line, col, null, leadingNewline);
    }

    public Token(Token.Type type, String text, int line, List<Object> lsonArgs) {
        this(type, text, line, -1, lsonArgs, false);
    }

    public Token(Token.Type type, String text, int line, int col, List<Object> lsonArgs, boolean leadingNewline) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.col = col;
        this.lsonArgs = lsonArgs;
        this.leadingNewline = leadingNewline;
    }

    // ---- getter / setter ----

    public Token.Type type() { return type; }
    public String text() { return text; }
    public List<Object> lsonArgs() { return lsonArgs; }
    public int line() { return line; }
    public int col() { return col; }
    public boolean leadingNewline() { return leadingNewline; }
    /** 标记此 token 为被抑制（不输出）。 */
    public void suppress() { this.suppressed = true; }
    public boolean suppressed() { return suppressed; }

}
