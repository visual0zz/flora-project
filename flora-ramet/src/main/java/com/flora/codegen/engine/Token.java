package com.flora.codegen.engine;

import java.util.List;

/**
 * 词法分析产物——由 {@link com.flora.codegen.engine.parser.Lexer} 生成的 Token 对象。
 *
 * <p>每个 Token 包含类型、原始文本、所在行号和可选的 Lson 参数列表。
 * Token 类型由 {@link Type} 枚举定义，涵盖指令关键字（IF、FOR 等）、
 * 变量引用（VAR）、纯文本（PASSIVE）和注释（COMMENT）等。
 *
 * <p>换行以独立的 {@link Type#NEW_LINE} token 表示，其文本为「换行符 + 其后的连续水平空白」，
 * 由 {@link com.flora.codegen.engine.parser.WhitespaceTrimmer} 在语法分析前统一规整。
 * 因此本类不再携带任何前导换行 / 抑制标记。
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
        /** 换行符：{@code \n}（或 {@code \r\n}）及其后的连续水平空白，规整前保留模板换行结构。 */
        NEW_LINE
    }

    private final Token.Type type;
    private final String text;
    private final List<Object> lsonArgs;
    private final int line;
    /** 列号（从 1 开始），-1 表示未知。 */
    private final int col;

    public Token(Token.Type type, String text, int line) {
        this(type, text, line, -1, null);
    }

    public Token(Token.Type type, String text, int line, int col) {
        this(type, text, line, col, null);
    }

    public Token(Token.Type type, String text, int line, List<Object> lsonArgs) {
        this(type, text, line, -1, lsonArgs);
    }

    public Token(Token.Type type, String text, int line, int col, List<Object> lsonArgs) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.col = col;
        this.lsonArgs = lsonArgs;
    }

    // ---- getter ----

    public Token.Type type() { return type; }
    public String text() { return text; }
    public List<Object> lsonArgs() { return lsonArgs; }
    public int line() { return line; }
    public int col() { return col; }

}
