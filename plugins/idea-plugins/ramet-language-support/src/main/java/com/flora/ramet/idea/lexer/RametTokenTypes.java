package com.flora.ramet.idea.lexer;

import com.intellij.psi.tree.IElementType;
import com.flora.ramet.idea.RametLanguage;

/**
 * Ramet 模板语言的 Token 类型常量定义。
 *
 * <p>对应 Ramet 引擎 {@code Token.Type} 枚举中的每个类型。
 */
public final class RametTokenTypes {

    private RametTokenTypes() {
    }

    // ---- 语法元素类型 ----

    /** 普通文本（不含模板语法的原样文本）。 */
    public static final IElementType PASSIVE      = new RametElementType("PASSIVE");
    /** 变量插值 {@code ${...}} 的内容。 */
    public static final IElementType VAR          = new RametElementType("VAR");
    /** 注释块 {@code <#-- ... -->} 的内容。 */
    public static final IElementType COMMENT      = new RametElementType("COMMENT");
    /** 条件开始 {@code <#if ...>}。 */
    public static final IElementType IF           = new RametElementType("IF");
    /** {@code <#else>} 标签。 */
    public static final IElementType ELSE         = new RametElementType("ELSE");
    /** {@code <#elseif ...>} 标签。 */
    public static final IElementType ELSEIF       = new RametElementType("ELSEIF");
    /** 结束标签 {@code </#>}。 */
    public static final IElementType END          = new RametElementType("END");
    /** 循环继续 {@code <#continue>}。 */
    public static final IElementType CONTINUE     = new RametElementType("CONTINUE");
    /** 循环退出 {@code <#break>}。 */
    public static final IElementType BREAK        = new RametElementType("BREAK");
    /** 元数据块 {@code <#meta>}。 */
    public static final IElementType META         = new RametElementType("META");
    /** 循环开始 {@code <#list ...>}。 */
    public static final IElementType FOR          = new RametElementType("FOR");
    /** 包含指令 {@code <#include ...>}。 */
    public static final IElementType INCLUDE      = new RametElementType("INCLUDE");
    /** 宏定义 {@code <#macro ...>}。 */
    public static final IElementType MACRO        = new RametElementType("MACRO");
    /** 宏调用 {@code <@name .../>}。 */
    public static final IElementType MACRO_CALL   = new RametElementType("MACRO_CALL");
    /** 换行符。 */
    public static final IElementType NEWLINE      = new RametElementType("NEWLINE");
    /** 空白符（空格、制表符等），由 PsiBuilder 自动跳过，必须被 token 覆盖。 */
    public static final IElementType WHITESPACE   = new RametElementType("WHITESPACE");

    // ---- 额外 PSI 元素类型 ----

    /** 指令名称关键字（if, list, include, macro 等）。 */
    public static final IElementType KEYWORD      = new RametElementType("KEYWORD");
    /** 字符串字面量。 */
    public static final IElementType STRING       = new RametElementType("STRING");
    /** 数字字面量。 */
    public static final IElementType NUMBER       = new RametElementType("NUMBER");
    /** 布尔/null 关键字。 */
    public static final IElementType BOOLEAN      = new RametElementType("BOOLEAN");
    /** 引用运算符（greaterThan, and, or 等）。 */
    public static final IElementType OPERATOR     = new RametElementType("OPERATOR");
    /** 点号属性访问（.）。 */
    public static final IElementType DOT          = new RametElementType("DOT");
    /** 圆括号。 */
    public static final IElementType PAREN        = new RametElementType("PAREN");
    /** 左圆括号 {@code (}. */
    public static final IElementType PAREN_L      = new RametElementType("PAREN_L");
    /** 右圆括号 {@code )}. */
    public static final IElementType PAREN_R      = new RametElementType("PAREN_R");
    /** 元数据注解（@Param, @Combine, @Path, @Config）。 */
    public static final IElementType ANNOTATION   = new RametElementType("ANNOTATION");
    /** 左尖括号 {@code <}。 */
    public static final IElementType LT           = new RametElementType("LT");
    /** 右尖括号 {@code >}。 */
    public static final IElementType GT           = new RametElementType("GT");
    /** 井号 {@code #}。 */
    public static final IElementType HASH         = new RametElementType("HASH");
    /** 美元符 {@code $}。 */
    public static final IElementType DOLLAR       = new RametElementType("DOLLAR");
    /** 左花括号 {。 */
    public static final IElementType LBRACE       = new RametElementType("LBRACE");
    /** 右花括号 }。 */
    public static final IElementType RBRACE       = new RametElementType("RBRACE");
    /** 斜杠 /。 */
    public static final IElementType SLASH        = new RametElementType("SLASH");
    /** 逗号 ,。 */
    public static final IElementType COMMA        = new RametElementType("COMMA");
    /** 冒号 :。 */
    public static final IElementType COLON        = new RametElementType("COLON");
    /** 范围运算符 ..。 */
    public static final IElementType RANGE        = new RametElementType("RANGE");
    /** 标识符（变量名、宏名等）。 */
    public static final IElementType IDENTIFIER   = new RametElementType("IDENTIFIER");
}
