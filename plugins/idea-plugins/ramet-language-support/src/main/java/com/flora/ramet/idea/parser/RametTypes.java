package com.flora.ramet.idea.parser;

import com.flora.ramet.idea.RametLanguage;
import com.intellij.psi.tree.IElementType;

/**
 * Ramet 模板语言的 PSI 复合节点类型定义。
 */
public final class RametTypes {

    private RametTypes() {
    }

    /** 文件根节点。 */
    public static final IElementType FILE = new RametNodeType("FILE");

    /** 指令节点（包含 &lt;#if&gt;, &lt;#list&gt;, &lt;#include&gt; 等）。 */
    public static final IElementType DIRECTIVE = new RametNodeType("DIRECTIVE");

    /** 宏调用节点（&lt;@name args/&gt;）。 */
    public static final IElementType MACRO_CALL = new RametNodeType("MACRO_CALL");

    /** 变量插值节点（${...}）。 */
    public static final IElementType VAR_INTERPOLATION = new RametNodeType("VAR_INTERPOLATION");

    /** 注释节点（&lt;#-- ... --&gt;）。 */
    public static final IElementType COMMENT = new RametNodeType("COMMENT");

    /** 普通文本节点。 */
    public static final IElementType TEXT = new RametNodeType("TEXT");

    /** 参数名标识符（用于 reference 解析）。 */
    public static final IElementType IDENTIFIER = new RametNodeType("IDENTIFIER");

    /** 字符串字面量节点（如 include 的路径）。 */
    public static final IElementType STRING_LITERAL = new RametNodeType("STRING_LITERAL");
}
